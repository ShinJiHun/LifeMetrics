#!/usr/bin/env python3
"""개발 요청함 서버 워커 — systemd 상시 서비스(--loop, 5초마다 확인). 잠금 파일로 한 번에 하나만 돈다.

앱(/dev-requests)에 쌓인 요청을 운영 API 에서 하나 가져와 처리한다.
  RUN     (QUEUED→RUNNING)   : worktree(devreq/<id>) 에서 Claude Code 실행 → 커밋 → 빌드 → dev SQL 적용 → dev 배포
                               (새 커밋이 없으면 = 질문에 답만 한 경우 → ANSWERED, 빌드·배포 생략)
  PROMOTE (APPROVED→PROMOTING): origin/master 머지 → 빌드 → 운영 SQL 적용 → push master → 운영 배포
끝난(DONE/REJECTED) 브랜치의 worktree 는 정리한다.

외부 패키지 없이 표준 라이브러리만 쓴다. 비밀값은 env 파일에서 읽는다:
  /mnt/200gb/project/data_pipeline/.env : DEV_WORKER_TOKEN, SLACK_WEBHOOK_URL, DB_USER/DB_PASSWORD(운영 SQL)
  /mnt/200gb/apps-dev/.env.db           : dev DB 계정(dev SQL)

설정(환경변수, systemd 는 /mnt/200gb/devreq/worker.env 에서 읽음):
  DEVREQ_API         요청함 API (기본 운영 http://localhost:8080/api/dev-requests)
  DEVREQ_BASE        작업 브랜치를 딸 기준 ref (기본 origin/master). origin/ 이 아니면 fetch 하지 않는다.
  DEVREQ_NO_PROMOTE  1 이면 운영 반영 요청을 실패 처리(개발 서버만 쓰는 시험 운영용)
  DEVREQ_POLL_S      --loop 확인 간격(초, 기본 5)

실행: python3 deploy/dev-request-worker.py          # 한 건 처리하고 끝
      python3 deploy/dev-request-worker.py --loop   # 상시(systemd: deploy/lifemetrics-devreq.service)
"""
from __future__ import annotations

import fcntl
import json
import os
import re
import shutil
import subprocess
import sys
import time
import traceback
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path

REPO = Path(os.environ.get("DEVREQ_REPO", "/mnt/200gb/project/lifemetrics"))   # 메인 체크아웃(브랜치·ref 공유)
WORK = Path(os.environ.get("DEVREQ_WORK", "/mnt/200gb/devreq"))                # worktree·로그
API = os.environ.get("DEVREQ_API", "http://localhost:8080/api/dev-requests")    # 운영 백엔드
BASE = os.environ.get("DEVREQ_BASE", "origin/master")                          # 작업 브랜치의 기준
NO_PROMOTE = os.environ.get("DEVREQ_NO_PROMOTE", "") == "1"
POLL_S = int(os.environ.get("DEVREQ_POLL_S", "5"))
PROD_ENV = Path("/mnt/200gb/project/data_pipeline/.env")
DEV_DB_ENV = Path("/mnt/200gb/apps-dev/.env.db")
DEV_DIR = Path("/mnt/200gb/apps-dev")
PROD_DIR = Path("/mnt/200gb/apps")
SERVER_IP = os.environ.get("DEVREQ_SERVER_IP", "34.172.162.148")
DB_CONTAINER = "riding-mariadb"
NODE_BIN = Path.home() / ".local/node/bin"

CLAUDE_TIMEOUT_S = 90 * 60
CLAUDE_BUDGET_USD = os.environ.get("DEVREQ_BUDGET_USD", "15")
HEARTBEAT_S = 60
SQL_DIR = "backend/src/main/resources/sql/"
SCHEMAS = ("riding_db", "journal_db", "lotto_db")
KST = timezone(timedelta(hours=9))

# Claude 가 쓸 수 있는 도구. 여기 없는 도구(WebFetch, git push, docker, 배포 스크립트 등)는 헤드리스 모드에서 거부된다.
ALLOWED_TOOLS = [
    "Read", "Edit", "Write", "Glob", "Grep", "TodoWrite",
    "Bash(./gradlew:*)", "Bash(cd backend && ./gradlew:*)",
    "Bash(npm ci)", "Bash(npm install:*)", "Bash(npm run:*)", "Bash(npx tsc:*)", "Bash(npx eslint:*)",
    "Bash(cd frontend && npm:*)", "Bash(cd frontend && npx:*)",
    "Bash(git status:*)", "Bash(git diff:*)", "Bash(git log:*)", "Bash(git show:*)",
    "Bash(git add:*)", "Bash(git commit:*)", "Bash(git restore:*)", "Bash(git mv:*)", "Bash(git rm:*)",
    "Bash(ls:*)", "Bash(cat:*)", "Bash(head:*)", "Bash(tail:*)", "Bash(wc:*)", "Bash(grep:*)", "Bash(rg:*)",
    "Bash(find:*)", "Bash(sed -n:*)", "Bash(mkdir:*)", "Bash(sort:*)", "Bash(uniq:*)", "Bash(jq:*)",
    "Bash(cd:*)", "Bash(/mnt/200gb/devreq/bin/devdb.sh:*)",
]
DISALLOWED_TOOLS = ["Bash(git push:*)", "Bash(git checkout:*)", "Bash(git switch:*)", "Bash(git reset:*)",
                    "Bash(git rebase:*)", "Bash(git merge:*)", "Bash(git worktree:*)", "WebFetch", "WebSearch"]

SYSTEM_PROMPT = """\
당신은 LifeMetrics(Spring Boot backend/ + React/Vite frontend/) 저장소에서 앱 관리자(저장소 주인)와 앱 안의
채팅 화면으로 대화하며 개발 요청을 처리하는 작업자입니다. 같은 대화의 다음 메시지는 이 세션을 이어서 받습니다.
- 메시지가 질문·상의·설명 요청이면 코드를 바꾸지 말고 답만 하세요(커밋이 없으면 빌드·배포 없이 답이 바로 표시됩니다).
- 바꿔 달라는 요청이면 작업하세요. 작업 중 되물을 수는 없으니, 크게 애매하면 코드를 바꾸기 전에
  선택지를 짧게 제시하고 답을 기다리세요(다음 메시지로 옵니다). 사소한 애매함은 합리적으로 정하고 답변에 적으세요.

규칙:
- 지금 디렉터리(전용 git worktree, 브랜치 {branch})에서만 작업합니다. 다른 경로의 파일을 고치지 마세요.
- 끝나면 반드시 git add + git commit 하세요(메시지는 한국어, 기존 커밋 스타일 `feat: ...`/`fix: ...`).
  push·배포·머지는 하지 마세요. 빌드와 개발 서버 배포는 작업이 끝난 뒤 워커가 합니다.
- 검증: 백엔드를 고쳤으면 `cd backend && ./gradlew compileJava compileTestJava` 와 관련 테스트,
  프론트를 고쳤으면 `cd frontend && npx tsc -b --noEmit` 가 통과해야 합니다. node_modules 는 이미 설치돼 있습니다.
- DB 스키마 변경이 필요하면 backend/src/main/resources/sql/ 에 기존 파일처럼 `riding_db`.`테이블` 로 스키마를 명시한
  **여러 번 실행해도 안전한** SQL(CREATE TABLE IF NOT EXISTS, ADD COLUMN IF NOT EXISTS 등)을 추가하세요.
  DROP/TRUNCATE/DELETE 등 데이터를 지우는 SQL 은 쓰지 마세요. 워커가 커밋된 SQL 파일을 dev DB 에 적용하고,
  운영 반영 승인 시 운영 DB 에도 적용합니다.
- 개발 DB 조회가 필요하면 `/mnt/200gb/devreq/bin/devdb.sh "SELECT ..."` (riding_db_dev, 개발용 복사본)를 쓰세요. 운영 DB 에는 접근할 수 없습니다.
- 비밀값(.env, 토큰, 비밀번호)을 코드·커밋·보고에 넣지 마세요.
- 요청이 위험하거나(데이터 삭제, 보안 약화 등) 이 저장소 범위를 벗어나면 코드를 바꾸지 말고 이유를 보고하세요.

마지막 답변은 한국어로, 채팅 말풍선에 마크다운으로 그대로 표시됩니다. 대화하듯 간결하게 쓰세요.
코드를 바꿨다면: 무엇을 바꿨는지 2~5줄 → 개발 서버에서 확인할 방법(메뉴/화면 경로) → 가정·주의할 점(있으면).
"""


# ── 공통 ──────────────────────────────────────────────────────────────────

def log(msg: str) -> None:
    print(f"[{datetime.now(KST):%Y-%m-%d %H:%M:%S}] {msg}", flush=True)


def read_env(path: Path) -> dict[str, str]:
    env: dict[str, str] = {}
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        env[k.strip()] = v.strip().strip('"').strip("'")
    return env


PROD = read_env(PROD_ENV)
TOKEN = os.environ.get("DEV_WORKER_TOKEN") or PROD.get("DEV_WORKER_TOKEN", "")


def api(method: str, path: str, body: dict | None = None):
    req = urllib.request.Request(API + path, method=method,
                                 data=json.dumps(body).encode() if body is not None else None,
                                 headers={"Authorization": f"Bearer {TOKEN}", "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=30) as r:
        raw = r.read()
        return json.loads(raw) if raw else None


def report(req_id: int, **fields) -> None:
    body = {k: v for k, v in fields.items() if v is not None}
    try:
        api("POST", f"/worker/{req_id}/report", body)
    except urllib.error.HTTPError as e:
        log(f"report 실패 {e.code}: {e.read()[:300]!r}")
    except Exception as e:  # noqa: BLE001 — 보고 실패로 작업을 죽이지 않는다
        log(f"report 실패: {e}")


def slack(text: str) -> None:
    url = os.environ.get("DEVREQ_SLACK_URL", PROD.get("SLACK_WEBHOOK_URL"))  # 테스트 때는 DEVREQ_SLACK_URL= 로 끔
    if not url:
        return
    try:
        urllib.request.urlopen(urllib.request.Request(url, data=json.dumps({"text": text}).encode(),
                                                      headers={"Content-Type": "application/json"}), timeout=10)
    except Exception as e:  # noqa: BLE001
        log(f"slack 실패: {e}")


class StepError(Exception):
    pass


class Job:
    """요청 하나를 처리하는 동안의 로그·heartbeat."""

    def __init__(self, req: dict, status: str):
        self.req = req
        self.id = req["id"]
        self.status = status
        self.lines: list[str] = []
        self.last_beat = 0.0
        WORK.joinpath("logs").mkdir(parents=True, exist_ok=True)
        self.logfile = WORK / "logs" / f"req-{self.id}.log"

    def note(self, line: str, beat: bool = False) -> None:
        line = line.rstrip()
        if not line:
            return
        stamped = f"{datetime.now(KST):%H:%M:%S} {line}"
        self.lines.append(stamped)
        with self.logfile.open("a") as f:
            f.write(stamped + "\n")
        log(f"#{self.id} {line[:200]}")
        if beat or time.time() - self.last_beat > HEARTBEAT_S:
            self.beat()

    def beat(self) -> None:
        self.last_beat = time.time()
        report(self.id, status=self.status, logTail="\n".join(self.lines[-400:]))

    def run(self, cmd: list[str], cwd: Path, label: str, timeout: int = 1800, env: dict | None = None,
            input_text: str | None = None) -> str:
        """명령 실행. 출력 끝부분을 로그에 남기고, 실패하면 StepError."""
        self.note(f"▶ {label}", beat=True)
        try:
            p = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True, timeout=timeout,
                               env=env or child_env(), input=input_text)
        except subprocess.TimeoutExpired:
            raise StepError(f"{label}: {timeout // 60}분 시간 초과")
        out = (p.stdout or "") + (p.stderr or "")
        if p.returncode != 0:
            tail = "\n".join(out.strip().splitlines()[-40:])
            self.note(tail)
            raise StepError(f"{label} 실패 (exit {p.returncode})\n{tail}")
        return out


def child_env() -> dict:
    env = dict(os.environ)
    env["PATH"] = f"{NODE_BIN}:{Path.home() / '.local/bin'}:{env.get('PATH', '/usr/bin:/bin')}"
    return env


def git(*args: str, cwd: Path = REPO, check: bool = True) -> str:
    p = subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True)
    if check and p.returncode != 0:
        raise StepError(f"git {' '.join(args)} 실패: {p.stderr.strip()[:500]}")
    return p.stdout.strip()


def fetch_base(cwd: Path = REPO) -> None:
    if BASE.startswith("origin/"):
        git("fetch", "-q", "origin", BASE.split("/", 1)[1], cwd=cwd)


def worktree_dir(branch: str) -> Path:
    return WORK / "wt" / branch.replace("/", "-")


def kst_now() -> str:
    return datetime.now(KST).strftime("%Y-%m-%d %H:%M:%S")


# ── 빌드·배포 ─────────────────────────────────────────────────────────────

def prepare_worktree(job: Job, branch: str, new: bool) -> Path:
    wt = worktree_dir(branch)
    if new:
        fetch_base()
        if wt.exists():
            raise StepError(f"{wt} 가 이미 있습니다")
        git("worktree", "add", "-q", "-b", branch, str(wt), BASE)
        job.note(f"worktree 생성: {wt} ({branch} ← {BASE} {git('rev-parse', '--short', BASE)})")
    elif not wt.exists():
        git("worktree", "prune")
        if git("rev-parse", "--verify", "-q", f"refs/heads/{branch}", check=False):
            git("worktree", "add", "-q", str(wt), branch)
            job.note(f"worktree 복구: {wt}")
        else:  # 앞 요청이 브랜치를 만들기 전에 실패한 경우
            fetch_base()
            git("worktree", "add", "-q", "-b", branch, str(wt), BASE)
            job.note(f"worktree 새로 생성: {wt}")
    # 지도 토큰 등 gitignore 된 프론트 설정
    src_env = REPO / "frontend/.env"
    if src_env.exists() and not (wt / "frontend/.env").exists():
        shutil.copy2(src_env, wt / "frontend/.env")
    if not (wt / "frontend/node_modules").exists():
        job.run(["npm", "ci", "--no-audit", "--no-fund"], wt / "frontend", "npm ci", timeout=900)
    return wt


def build(job: Job, wt: Path) -> None:
    job.run(["./gradlew", "-q", "clean", "bootJar"], wt / "backend", "백엔드 빌드(bootJar)", timeout=1500)
    job.run(["npm", "run", "build"], wt / "frontend", "프론트 빌드", timeout=900)


def built_jar(wt: Path) -> Path:
    jars = [p for p in (wt / "backend/build/libs").glob("*.jar") if not p.name.endswith("-plain.jar")]
    if len(jars) != 1:
        raise StepError(f"bootJar 결과를 찾지 못함: {[p.name for p in jars]}")
    return jars[0]


def write_deploy_json(static_dir: Path, env_name: str, commit: str) -> None:
    (static_dir / "deploy.json").write_text(json.dumps(
        {"env": env_name, "deployedAt": kst_now(), "builtAt": kst_now(), "commit": commit},
        separators=(",", ":")) + "\n")


def install_artifacts(job: Job, wt: Path, target: Path, env_name: str) -> None:
    commit = git("rev-parse", "--short", "HEAD", cwd=wt)
    target.joinpath("static").mkdir(parents=True, exist_ok=True)
    if (target / "lifemetrics.jar").exists():
        shutil.copy2(target / "lifemetrics.jar", target / "lifemetrics.jar.prev")
    subprocess.run(["rsync", "-a", "--delete", f"{target}/static/", f"{target}/static.prev/"], check=True)
    shutil.copy2(built_jar(wt), target / "lifemetrics.jar")
    shutil.copy2(wt / "deploy/Dockerfile", target / "Dockerfile")
    subprocess.run(["rsync", "-a", "--delete", f"{wt}/frontend/dist/", f"{target}/static/"], check=True)
    write_deploy_json(target / "static", env_name, commit)
    job.note(f"산출물 설치: {target} (commit {commit})")


def deploy(job: Job, wt: Path, env_name: str) -> None:
    # remote.sh 는 worktree 의 것을 쓴다(= 이번에 배포하는 코드와 같은 버전)
    job.run(["bash", str(wt / "deploy/remote.sh"), env_name, SERVER_IP], wt, f"{env_name} 배포(remote.sh)",
            timeout=600)


def changed_sql(wt: Path) -> list[str]:
    out = git("diff", "--name-only", "--diff-filter=AM", f"{BASE}...HEAD", "--", SQL_DIR, cwd=wt)
    return [f for f in out.splitlines() if f.endswith(".sql")]


DANGEROUS_SQL = re.compile(r"\b(DROP\s+(TABLE|DATABASE|SCHEMA|COLUMN)|TRUNCATE|DELETE\s+FROM)\b", re.I)


def apply_sql(job: Job, wt: Path, files: list[str], target: str) -> None:
    """target=dev: 스키마 한정자를 *_dev 로 바꿔 lm_dev 로 적용. target=prod: 운영 계정으로 그대로 적용."""
    if not files:
        return
    if target == "dev":
        env = read_env(DEV_DB_ENV)
    else:
        env = PROD
    user, pw = env["DB_USER"], env["DB_PASSWORD"]
    for f in files:
        sql = (wt / f).read_text()
        if DANGEROUS_SQL.search(sql):
            raise StepError(f"{f}: 데이터를 지우는 SQL(DROP/TRUNCATE/DELETE)이 있어 자동 적용하지 않습니다. 직접 확인해 주세요.")
        if target == "dev":
            for s in SCHEMAS:
                sql = sql.replace(f"`{s}`.", f"`{s}_dev`.").replace(f" {s}.", f" {s}_dev.")
        db = "riding_db_dev" if target == "dev" else "riding_db"
        job.run(["docker", "exec", "-i", "-e", f"MYSQL_PWD={pw}", DB_CONTAINER, "mariadb", f"-u{user}", db],
                wt, f"SQL 적용({target}): {f}", timeout=300, input_text=sql)


# ── Claude ────────────────────────────────────────────────────────────────

def describe_event(ev: dict) -> list[str]:
    """stream-json 이벤트를 사람이 읽을 진행 로그 한두 줄로."""
    out: list[str] = []
    if ev.get("type") == "assistant":
        for c in ev.get("message", {}).get("content", []):
            if c.get("type") == "text" and c.get("text", "").strip():
                out.append("💬 " + c["text"].strip().replace("\n", " ")[:300])
            elif c.get("type") == "tool_use":
                inp = c.get("input", {})
                arg = inp.get("command") or inp.get("file_path") or inp.get("pattern") or ""
                out.append(f"🔧 {c.get('name')} {str(arg)[:200]}")
    return out


def run_claude(job: Job, wt: Path, prompt: str, branch: str, resume: str | None) -> dict:
    cmd = ["claude", "-p", prompt, "--output-format", "stream-json", "--verbose",
           "--permission-mode", "acceptEdits",
           "--allowedTools", *ALLOWED_TOOLS, "--disallowedTools", *DISALLOWED_TOOLS,
           "--append-system-prompt", SYSTEM_PROMPT.format(branch=branch),
           "--max-budget-usd", CLAUDE_BUDGET_USD]
    if resume:
        cmd += ["--resume", resume]
    job.note("▶ Claude 작업 시작" + (f" (세션 이어서 {resume[:8]})" if resume else ""), beat=True)
    proc = subprocess.Popen(cmd, cwd=wt, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
                            env=child_env(), stdin=subprocess.DEVNULL)
    result: dict = {}
    deadline = time.time() + CLAUDE_TIMEOUT_S
    assert proc.stdout is not None
    for line in proc.stdout:
        if time.time() > deadline:
            proc.kill()
            raise StepError(f"Claude 작업이 {CLAUDE_TIMEOUT_S // 60}분을 넘어 중단했습니다.")
        line = line.strip()
        if not line:
            continue
        try:
            ev = json.loads(line)
        except json.JSONDecodeError:
            job.note(line[:300])
            continue
        if ev.get("type") == "result":
            result = ev
        for d in describe_event(ev):
            job.note(d)
    proc.wait()
    if not result:
        raise StepError(f"Claude 가 결과 없이 끝났습니다 (exit {proc.returncode})")
    return result


# ── 작업 ──────────────────────────────────────────────────────────────────

def handle_run(req: dict) -> None:
    job = Job(req, "RUNNING")
    branch = req.get("branch") or f"devreq/{req['id']}"
    new = not req.get("branch")
    slack(f"🛠 개발 요청 #{req['id']} 작업 시작: {req['title']}")
    try:
        report(job.id, status="RUNNING", branch=branch)
        wt = prepare_worktree(job, branch, new)

        prompt = (f"[메시지 #{req['id']}]\n{req['body']}" if new else
                  f"[메시지 #{req['id']} — 앞 대화 이어서]\n{req['body']}")
        head_before = git("rev-parse", "HEAD", cwd=wt)
        res = run_claude(job, wt, prompt, branch, req.get("sessionId"))
        summary = (res.get("result") or "").strip()
        cost = res.get("total_cost_usd")
        report(job.id, sessionId=res.get("session_id"), summary=summary or None, costUsd=cost)
        if res.get("is_error"):
            raise StepError(f"Claude 오류: {res.get('subtype')} {summary[:500]}")

        # Claude 가 커밋을 빠뜨렸으면 대신 커밋
        if git("status", "--porcelain", cwd=wt):
            git("add", "-A", cwd=wt)
            git("commit", "-q", "-m", f"chore(devreq #{req['id']}): 작업 내용 커밋(자동)", cwd=wt)
            job.note("커밋되지 않은 변경을 자동 커밋")

        ahead = git("rev-list", "--count", f"{BASE}..HEAD", cwd=wt)
        if git("rev-parse", "HEAD", cwd=wt) == head_before:
            # 이번 메시지에서 코드가 안 바뀜 = 답만 함. 대화 브랜치에 앞서 만든 커밋이 있으면 그걸로 운영 반영 가능.
            if not summary:
                raise StepError("Claude 가 답 없이 끝났습니다.")
            sha = git("rev-parse", "HEAD", cwd=wt) if ahead != "0" else None
            job.note("💬 코드 변경 없음 — 답변만")
            report(job.id, status="ANSWERED", commitSha=sha, logTail="\n".join(job.lines[-400:]))
            return

        diff_stat = git("diff", "--stat", f"{BASE}...HEAD", cwd=wt)
        sha = git("rev-parse", "HEAD", cwd=wt)
        report(job.id, commitSha=sha, diffStat=diff_stat)

        build(job, wt)
        apply_sql(job, wt, changed_sql(wt), "dev")
        install_artifacts(job, wt, DEV_DIR, "dev")
        deploy(job, wt, "dev")

        job.note("✅ 개발 서버 배포 완료")
        report(job.id, status="DEV_READY", logTail="\n".join(job.lines[-400:]))
        slack(f"✅ 개발 요청 #{req['id']} 개발 서버 반영: {req['title']}\nhttp://{SERVER_IP}:8081 → 확인 후 앱에서 운영 반영")
    except Exception as e:  # noqa: BLE001
        fail(job, e)


def handle_promote(req: dict) -> None:
    job = Job(req, "PROMOTING")
    branch = req.get("branch")
    slack(f"🚀 개발 요청 #{req['id']} 운영 반영 시작: {req['title']}")
    try:
        if NO_PROMOTE:
            raise StepError("지금 워커는 개발 서버 시험 모드(DEVREQ_NO_PROMOTE=1)라 운영 반영을 하지 않습니다.")
        if not branch:
            raise StepError("브랜치 정보가 없습니다")
        wt = prepare_worktree(job, branch, new=False)
        if git("status", "--porcelain", cwd=wt):
            raise StepError("worktree 에 커밋되지 않은 변경이 있습니다")
        approved_sha = req.get("commitSha")
        if approved_sha and git("rev-parse", "HEAD", cwd=wt) != approved_sha:
            raise StepError("승인한 커밋과 브랜치 HEAD 가 다릅니다(그 사이 변경됨)")

        git("fetch", "-q", "origin", "master", cwd=wt)
        p = subprocess.run(["git", "merge", "--no-edit", "-m",
                            f"Merge devreq #{req['id']}: {req['title']}", "origin/master"],
                           cwd=wt, capture_output=True, text=True)
        if p.returncode != 0:
            subprocess.run(["git", "merge", "--abort"], cwd=wt)
            raise StepError("origin/master 와 충돌합니다. 추가 요청으로 충돌 해결을 지시해 주세요.\n" + p.stdout[-1500:])
        job.note(f"origin/master 반영: {git('log', '--oneline', '-1', cwd=wt)}")

        build(job, wt)
        apply_sql(job, wt, changed_sql(wt), "prod")
        # 운영에 올리는 코드는 항상 master 에 먼저 있어야 한다
        git("push", "-q", "origin", "HEAD:master", cwd=wt)
        job.note("origin/master push 완료")
        subprocess.run(["git", "fetch", "-q", "origin", "master:master"], cwd=REPO,
                       capture_output=True)  # master 가 어딘가 체크아웃돼 있으면 실패해도 무방
        install_artifacts(job, wt, PROD_DIR, "prod")
        deploy(job, wt, "prod")

        sha = git("rev-parse", "HEAD", cwd=wt)
        job.note("✅ 운영 배포 완료")
        report(job.id, status="DONE", commitSha=sha, logTail="\n".join(job.lines[-400:]))
        slack(f"🎉 개발 요청 #{req['id']} 운영 반영 완료: {req['title']}\nhttps://ride.tho881.me")
    except Exception as e:  # noqa: BLE001
        fail(job, e)


def fail(job: Job, e: Exception) -> None:
    msg = str(e) if isinstance(e, StepError) else f"{type(e).__name__}: {e}\n{traceback.format_exc()[-1500:]}"
    job.note(f"❌ {msg.splitlines()[0] if msg else '실패'}")
    report(job.id, status="FAILED", error=msg[:5000], logTail="\n".join(job.lines[-400:]))
    slack(f"❌ 개발 요청 #{job.id} 실패: {job.req.get('title')}\n{msg[:300]}")


def cleanup() -> None:
    """운영 반영됐거나 반려·취소된 브랜치의 worktree 를 지운다(node_modules·빌드 산출물 용량)."""
    root = WORK / "wt"
    if not root.exists():
        return
    for wt in root.iterdir():
        m = re.fullmatch(r"devreq-(\d+)", wt.name)
        if not m:
            continue
        branch = f"devreq/{m.group(1)}"
        try:
            status = api("POST", "/worker/branch-status", {"branch": branch}).get("status", "")
        except Exception as e:  # noqa: BLE001
            log(f"cleanup 상태 조회 실패 {branch}: {e}")
            continue
        if status in ("DONE", "REJECTED", "CANCELLED"):
            subprocess.run(["git", "worktree", "remove", "--force", str(wt)], cwd=REPO, capture_output=True)
            if status == "DONE":
                subprocess.run(["git", "branch", "-D", branch], cwd=REPO, capture_output=True)
            log(f"worktree 정리: {wt.name} ({status})")


DEVDB_SH = """#!/bin/bash
# 개발 요청 워커가 Claude 에게 주는 dev DB 조회 도구. lm_dev 계정(= *_dev 스키마만 접근 가능).
# dev-request-worker.py 가 매번 다시 쓴다(수정해도 덮어써짐).
set -euo pipefail
PW=$(grep '^DB_PASSWORD=' /mnt/200gb/apps-dev/.env.db | cut -d= -f2-)
exec docker exec -i -e MYSQL_PWD="$PW" riding-mariadb mariadb -ulm_dev --table riding_db_dev -e "$1"
"""


def install_helpers() -> None:
    """Claude 가 쓰는 헬퍼는 worktree 밖(Claude 가 고칠 수 없는 곳)에 둔다."""
    path = WORK / "bin" / "devdb.sh"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(DEVDB_SH)
    path.chmod(0o755)


def process_one() -> bool | None:
    """한 건 처리. 처리했으면 True, 할 일이 없으면 False, 서버에 못 붙으면 None."""
    try:
        req = api("POST", "/worker/claim")
    except (urllib.error.URLError, TimeoutError, ConnectionError) as e:
        log(f"claim 실패(백엔드 응답 없음?): {e}")
        return None
    if not req:
        return False
    log(f"작업 #{req['id']} [{req['status']}] {req['title']}")
    install_helpers()
    if req["status"] == "PROMOTING":
        handle_promote(req)
    else:
        handle_run(req)
    return True


def main() -> int:
    if not TOKEN:
        log("DEV_WORKER_TOKEN 이 없습니다")
        return 1
    WORK.mkdir(parents=True, exist_ok=True)
    lock = (WORK / "worker.lock").open("w")
    try:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        log("다른 워커가 실행 중입니다")
        return 0
    if "--loop" not in sys.argv:
        done = process_one()
        if done is False:
            cleanup()
        return 1 if done is None else 0

    log(f"상시 실행 시작: api={API} base={BASE} promote={'off' if NO_PROMOTE else 'on'} poll={POLL_S}s")
    last_cleanup = 0.0
    while True:
        try:
            done = process_one()
        except Exception:  # noqa: BLE001 — 한 건에서 예상 못 한 오류가 나도 서비스는 계속
            log(traceback.format_exc())
            done = None
        if done:
            continue  # 쌓인 다음 건 바로
        if done is False and time.time() - last_cleanup > 600:
            try:
                cleanup()
            except Exception:  # noqa: BLE001
                log(traceback.format_exc())
            last_cleanup = time.time()
        time.sleep(POLL_S if done is False else 30)


if __name__ == "__main__":
    sys.exit(main())
