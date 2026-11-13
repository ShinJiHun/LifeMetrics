#!/bin/bash
# prod DB(riding_db / journal_db / lotto_db) → dev DB(*_dev) 로 통째로 복사한다. 서버에서 실행.
#   bash deploy/refresh-dev-db.sh            # 세 스키마 전부
#   bash deploy/refresh-dev-db.sh riding_db  # 일부만
#
# - dev DB 의 기존 내용은 전부 지워진다(개발 중 넣은 데이터 포함).
# - prod 는 --single-transaction 으로 읽기만 하므로 운영 중에 돌려도 된다(잠금 없음).
# - 뷰/트리거의 DEFINER 는 dev 계정(lm_dev@172.%)으로 바꾸고, 정의 안의 `riding_db`. 같은
#   스키마 한정자도 *_dev 로 바꾼다 → dev 계정이 뷰를 통해 prod 를 읽는 일이 없게.
# - dev 계정/비번은 /mnt/200gb/apps-dev/.env.db, root 비번은 mariadb/.env 에서 읽는다.
set -euo pipefail

CONTAINER=riding-mariadb
ROOT_ENV=/mnt/200gb/project/mariadb/.env
DEV_USER="lm_dev"
DEV_HOST="172.%"
if [ $# -gt 0 ]; then SCHEMAS=("$@"); else SCHEMAS=(riding_db journal_db lotto_db); fi

ROOT_PW=$(grep '^MARIADB_ROOT_PASSWORD=' "$ROOT_ENV" | cut -d= -f2-)
[ -n "$ROOT_PW" ] || { echo "❌ $ROOT_ENV 에서 MARIADB_ROOT_PASSWORD 를 못 읽음"; exit 1; }

sql() { docker exec -i -e MYSQL_PWD="$ROOT_PW" "$CONTAINER" mariadb -uroot "$@"; }

# 한정자 치환용 sed 식: `riding_db`. → `riding_db_dev`.  (스키마 전부)
SED_ARGS=(-e "s/DEFINER=\`[^\`]*\`@\`[^\`]*\`/DEFINER=\`$DEV_USER\`@\`$DEV_HOST\`/g")
for s in riding_db journal_db lotto_db; do
    SED_ARGS+=(-e "s/\`$s\`\\./\`${s}_dev\`./g")
done

for SRC in "${SCHEMAS[@]}"; do
    case "$SRC" in riding_db|journal_db|lotto_db) ;; *) echo "❌ 알 수 없는 스키마: $SRC"; exit 1 ;; esac
    DST="${SRC}_dev"
    echo "📦 $SRC → $DST"
    start=$(date +%s)

    # 대상 비우기: DROP/CREATE 하면 권한은 남지만 기본 collation 을 다시 맞춰야 하므로 src 값을 그대로 씀
    coll=$(sql -N -e "SELECT DEFAULT_COLLATION_NAME FROM information_schema.schemata WHERE schema_name='$SRC'")
    sql -e "DROP DATABASE IF EXISTS \`$DST\`; CREATE DATABASE \`$DST\` COLLATE $coll;"

    # 1) 테이블·트리거·루틴. 뷰는 뺀다: prod 에 정의자 권한이 깨진 뷰가 있으면 dump 가 통째로 실패하므로
    #    (예: v_persona_corpus — DEFINER tho881@172.18.0.1 에 riding_db 권한 없음).
    mapfile -t views < <(sql -N -e "SELECT TABLE_NAME FROM information_schema.views WHERE TABLE_SCHEMA='$SRC'")
    ignore=()
    for v in "${views[@]}"; do ignore+=(--ignore-table="$SRC.$v"); done

    docker exec -e MYSQL_PWD="$ROOT_PW" "$CONTAINER" \
        mariadb-dump -uroot --single-transaction --quick --routines --triggers --events \
                     --hex-blob --default-character-set=utf8mb4 "${ignore[@]}" "$SRC" \
      | sed "${SED_ARGS[@]}" \
      | sql "$DST"

    # 2) 뷰: information_schema 정의로 다시 만든다(뷰끼리 참조할 수 있어 성공할 때까지 몇 바퀴 돈다)
    pending=("${views[@]}")
    for _ in 1 2 3 4 5; do
        [ ${#pending[@]} -eq 0 ] && break
        next=()
        for v in "${pending[@]}"; do
            def=$(sql -N --raw -e "SELECT VIEW_DEFINITION FROM information_schema.views WHERE TABLE_SCHEMA='$SRC' AND TABLE_NAME='$v'" | sed "${SED_ARGS[@]}")
            if ! printf 'CREATE OR REPLACE DEFINER=`%s`@`%s` SQL SECURITY DEFINER VIEW `%s` AS %s;\n' \
                    "$DEV_USER" "$DEV_HOST" "$v" "$def" | sql "$DST" 2>/dev/null; then
                next+=("$v")
            fi
        done
        pending=("${next[@]+"${next[@]}"}")
    done
    [ ${#pending[@]} -eq 0 ] || echo "   ⚠️  만들지 못한 뷰: ${pending[*]}"

    tables=$(sql -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DST'")
    echo "   ✅ ${tables}개 테이블/뷰, $(( $(date +%s) - start ))초"
done

# 검증: dev 스키마의 뷰/트리거/루틴이 prod 스키마를 참조하지 않는지
leak=$(sql -N -e "
  SELECT CONCAT(TABLE_SCHEMA,'.',TABLE_NAME) FROM information_schema.views
   WHERE TABLE_SCHEMA LIKE '%\\_dev' AND VIEW_DEFINITION REGEXP '\`(riding|journal|lotto)_db\`\\.'
  UNION ALL
  SELECT CONCAT(TRIGGER_SCHEMA,'.',TRIGGER_NAME) FROM information_schema.triggers
   WHERE TRIGGER_SCHEMA LIKE '%\\_dev' AND ACTION_STATEMENT REGEXP '(riding|journal|lotto)_db\\.'")
if [ -n "$leak" ]; then
    echo "⚠️  prod 스키마를 참조하는 dev 객체가 있음:"; echo "$leak"; exit 1
fi
echo "🎉 dev DB 갱신 완료"
