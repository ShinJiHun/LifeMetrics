# sql/local/ — 커밋하지 않는 개인정보 SQL

이 폴더의 `*.sql` 은 `.gitignore` 처리된다 (이 README 만 추적).

전화번호·퇴사 사유·구직 상황처럼 **공개 저장소에 두면 안 되는 실제 값**이 담긴
`INSERT`/`UPDATE` 문을 여기에 둔다. 스키마(CREATE/ALTER)는 상위 `sql/` 에 커밋한다.

주입은 1회성이며, 이후 값 변경은 관리자 화면(프로필 관리)에서 한다.

```
mysql -h 127.0.0.1 -P 3307 -u tho881 -p journal_db < local/career_personal_data.sql
```
