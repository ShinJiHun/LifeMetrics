#!/bin/bash
# 운영 DB → 개발용 DB 복제 (riding_db / journal_db / lotto_db → *_dev)
#
#   ./deploy/clone-dev-db.sh           *_dev 가 없는 것만 복제 (이미 있으면 건너뜀)
#   FORCE=1 ./deploy/clone-dev-db.sh   기존 *_dev 를 지우고 운영에서 다시 복제
#
# - 운영 DB 는 읽기만 한다(mariadb-dump --single-transaction, 잠금 없음). 쓰기/삭제 대상은 *_dev 뿐.
# - 개발 컨테이너(deploy/remote.sh dev)가 이 *_dev DB 를 바라본다.
# - 건강검진/체성분 등 개인 데이터가 그대로 복사된다. 개발 서버 접근을 제한적으로 유지할 것.
# - CREATE DATABASE 권한이 없다는 오류가 나면 DB 관리자 계정으로 아래를 한 번 실행:
#     GRANT ALL PRIVILEGES ON `%\_dev`.* TO '<DB_USER>'@'%';
set -euo pipefail

SERVER="34.172.162.148"
USER="jihoon"
SSH_KEY="${SSH_KEY:-$HOME/.ssh/riding_key_nopass}"

ssh -i "$SSH_KEY" "$USER@$SERVER" "FORCE=${FORCE:-0} bash -s" <<'REMOTE'
set -euo pipefail
ENV_FILE=/mnt/200gb/project/data_pipeline/.env   # 운영 컨테이너가 쓰는 것과 같은 파일
DBS="riding_db journal_db lotto_db"

# .env 를 source 하지 않고 필요한 두 값만 꺼낸다(셸 문법이 아닌 값이 있어도 안전)
DB_USER=$(grep -E '^DB_USER=' "$ENV_FILE" | head -1 | cut -d= -f2-)
DB_PASSWORD=$(grep -E '^DB_PASSWORD=' "$ENV_FILE" | head -1 | cut -d= -f2-)
[ -n "$DB_USER" ] && [ -n "$DB_PASSWORD" ] || { echo "❌ $ENV_FILE 에서 DB_USER/DB_PASSWORD 를 찾지 못함"; exit 1; }

# 비밀번호를 프로세스 인자에 노출하지 않도록 MYSQL_PWD 로 전달
# 주의: 이 스크립트는 ssh 의 'bash -s' (stdin) 로 실행되므로, stdin 이 필요 없는 호출에는 -i 를 쓰지 않는다
#       (docker exec -i 가 스크립트 본문을 먹어버림). 임포트(파이프 입력)에만 MI 를 쓴다.
M()  { docker exec    -e MYSQL_PWD="$DB_PASSWORD" riding-mariadb mariadb      -u"$DB_USER" "$@"; }
D()  { docker exec    -e MYSQL_PWD="$DB_PASSWORD" riding-mariadb mariadb-dump -u"$DB_USER" "$@"; }
MI() { docker exec -i -e MYSQL_PWD="$DB_PASSWORD" riding-mariadb mariadb      -u"$DB_USER" "$@"; }

for db in $DBS; do
    dev="${db}_dev"

    src_exists=$(M -N -e "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$db'")
    if [ "$src_exists" = 0 ]; then echo "⚠️  $db 가 운영에 없음 — 건너뜀"; continue; fi

    dev_exists=$(M -N -e "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$dev'")
    if [ "$dev_exists" != 0 ] && [ "$FORCE" != 1 ]; then
        echo "⏭️  $dev 이미 있음 — 건너뜀 (다시 복제하려면 FORCE=1)"
        continue
    fi

    # 원본과 같은 문자셋/콜레이션으로 생성
    read -r charset collation < <(M -N -e "SELECT default_character_set_name, default_collation_name FROM information_schema.schemata WHERE schema_name='$db'")
    echo "📦 $db → $dev ($charset / $collation)"
    M -e "DROP DATABASE IF EXISTS \`$dev\`; CREATE DATABASE \`$dev\` CHARACTER SET $charset COLLATE $collation"

    D --single-transaction --routines --triggers --events "$db" | MI "$dev"

    src_tables=$(M -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$db'")
    dev_tables=$(M -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$dev'")
    echo "   ✓ 테이블 $src_tables → $dev_tables"
    [ "$src_tables" = "$dev_tables" ] || { echo "   ❌ 테이블 수가 다름 — 복제 확인 필요"; exit 1; }
done
echo "완료. 다음: ./deploy.sh dev"
REMOTE
