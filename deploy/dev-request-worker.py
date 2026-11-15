#!/usr/bin/env python3
"""개발 요청함 서버 워커 — systemd 상시 서비스. 잠금 파일로 워커는 하나만 돈다.

앱(/dev-requests)의 대화마다 Claude 프로세스를 하나씩 띄워 두고(상시 대화), 백엔드를 long-poll 해서 일을 받는다.
  대화    (dev_conversation OPEN/LIVE): worktree(devreq/<root>) 에서 `claude -p --input-format stream-json` 을 띄워 둔다.
                               입력이 IDLE_MIN(기본 30)분 없으면 닫는다. 세션 ID 는 남으므로 다시 열면 --resume 으로 이어진다.
  MESSAGE (QUEUED→RUNNING)   : 살아 있는 프로세스 stdin 에 메시지를 넣고 답(result)을 받는다 → ANSWERED
                               (코드를 바꿨으면 커밋까지만, 빌드·배포는 하지 않음)
  DEPLOY  (QUEUED→RUNNING)   : [dev 배포]/[대화 종료] — 빌드 → dev SQL 적용 → dev 배포 → DEV_READY
  PROMOTE (APPROVED→PROMOTING): origin/master 머지 → 빌드 → 운영 SQL 적용 → push master → 운영 배포
메시지는 대화마다 동시에, 빌드가 필요한 일(DEPLOY/PROMOTE)은 한 번에 하나(백엔드가 보장). 끝난 대화의 worktree 는 정리한다.

외부 패키지 없이 표준 라이브러리만 쓴다. 비밀값은 env 파일에서 읽는다:
  /mnt/200gb/project/data_pipeline/.env : DEV_WORKER_TOKEN, SLACK_WEBHOOK_URL, DB_USER/DB_PASSWORD(운영 SQL)
  /mnt/200gb/apps-dev/.env.db           : dev DB 계정(dev SQL)

설정(환경변수, systemd 는 /mnt/200gb/devreq/worker.env 에서 읽음):
  DEVREQ_API         요청함 API (기본 개발 서버 http://localhost:8081/api/dev-requests — 요청함은 개발 서버 전용)
  DEVREQ_BASE        작업 브랜치를 딸 기준 ref (기본 origin/master). origin/ 이 아니면 fetch 하지 않는다.
  DEVREQ_DP_REPO     data_pipeline 저장소(기본 /mnt/200gb/project/data_pipeline), DEVREQ_DP_BASE 기준 ref(기본 origin/main)
  DEVREQ_NO_PROMOTE  1 이면 운영 반영 요청을 실패 처리(개발 서버만 쓰는 시험 운영용)
  DEVREQ_IDLE_MIN    대화 자동 종료(분, 기본 30)

실행: python3 deploy/dev-request-worker.py --loop   # 상시(systemd: deploy/lifemetrics-devreq.service)
"""
from __future__ import annotations

import fcntl
import json
import os
import queue
import re
import shutil
import signal
import subprocess
import sys
import threading
import time
import traceback
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path

REPO = Path(os.environ.get("DEVREQ_REPO", "/mnt/200gb/project/lifemetrics"))   # 메인 체크아웃(브랜치·ref 공유)
WORK = Path(os.environ.get("DEVREQ_WORK", "/mnt/200gb/devreq"))                # worktree·로그
# 개발 요청함은 개발 서버 전용(운영 백엔드는 DEV_REQUESTS_ENABLED 가 없어 404)
API = os.environ.get("DEVREQ_API", "http://localhost:8081/api/dev-requests")
BASE = os.environ.get("DEVREQ_BASE", "origin/master")                          # 작업 브랜치의 기준
# data_pipeline: 별도 저장소. 서버의 이 체크아웃이 곧 운영(cron 등이 여기서 실행)이라 운영 반영 = main push + ff 갱신
PIPELINE_REPO = Path(os.environ.get("DEVREQ_DP_REPO", "/mnt/200gb/project/data_pipeline"))
PIPELINE_BASE = os.environ.get("DEVREQ_DP_BASE", "origin/main")
NO_PROMOTE = os.environ.get("DEVREQ_NO_PROMOTE", "") == "1"
IDLE_CLOSE_S = int(os.environ.get("DEVREQ_IDLE_MIN", "30")) * 60
POLL_WAIT_MS = 20_000
PROD_ENV = Path("/mnt/200gb/project/data_pipeline/.env")
DEV_DB_ENV = Path("/mnt/200gb/apps-dev/.env.db")
DEV_DIR = Path("/mnt/200gb/apps-dev")
PROD_DIR = Path("/mnt/200gb/apps")
SERVER_IP = os.environ.get("DEVREQ_SERVER_IP", "34.172.162.148")
DB_CONTAINER = "riding-mariadb"
NODE_BIN = Path.home() / ".local/node/bin"

CLAUDE_TIMEOUT_S = 90 * 60  # 메시지 하나에 대한 답 제한
CLAUDE_BUDGET_USD = os.environ.get("DEVREQ_BUDGET_USD", "15")
HEARTBEAT_S = 60
SQL_DIR = "backend/src/main/resources/sql/"
SCHEMAS = ("riding_db", "journal_db", "lotto_db")
KST = timezone(timedelta(hours=9))

# Claude 가 쓸 수 있는 도구. 여기 없는 도구(WebFetch, git push, docker, 배포 스크립트 등)는 헤드리스 모드에서 거부된다.
COMMON_TOOLS = [
    "Read", "Edit", "Write", "Glob", "Grep", "TodoWrite",
    "Bash(git status:*)", "Bash(git diff:*)", "Bash(git log:*)", "Bash(git show:*)",
    "Bash(git add:*)", "Bash(git commit:*)", "Bash(git restore:*)", "Bash(git mv:*)", "Bash(git rm:*)",
    "Bash(ls:*)", "Bash(cat:*)", "Bash(head:*)", "Bash(tail:*)", "Bash(wc:*)", "Bash(grep:*)", "Bash(rg:*)",
    "Bash(find:*)", "Bash(sed -n:*)", "Bash(mkdir:*)", "Bash(sort:*)", "Bash(uniq:*)", "Bash(jq:*)",
    "Bash(cd:*)", "Bash(/mnt/200gb/devreq/bin/devdb.sh:*)",
]
APP_TOOLS = COMMON_TOOLS + [
    "Bash(./gradlew:*)", "Bash(cd backend && ./gradlew:*)",
    "Bash(npm ci)", "Bash(npm install:*)", "Bash(npm run:*)", "Bash(npx tsc:*)", "Bash(npx eslint:*)",
    "Bash(cd frontend && npm:*)", "Bash(cd frontend && npx:*)",
]
# 파이프라인은 스크립트를 실제로 돌리면 운영 DB·외부 API 에 쓸 수 있어 문법 검사만 허용한다(pytest 도 import 시 .env 를 읽을 수 있어 제외)
PIPELINE_TOOLS = COMMON_TOOLS + ["Bash(python3 -m py_compile:*)", "Bash(python3 -m compileall:*)"]
DISALLOWED_TOOLS = ["Bash(git push:*)", "Bash(git checkout:*)", "Bash(git switch:*)", "Bash(git reset:*)",
                    "Bash(git rebase:*)", "Bash(git merge:*)", "Bash(git worktree:*)", "WebFetch", "WebSearch"]

SYSTEM_PROMPT = """\
당신은 LifeMetrics(Spring Boot backend/ + React/Vite frontend/) 저장소에서 앱 관리자(저장소 주인)와 앱 안의
채팅 화면으로 실시간 대화하며 개발 요청을 처리하는 작업자입니다. 대화가 열려 있는 동안 이 프로세스가 계속 살아 있고,
관리자의 다음 메시지가 바로 이어서 들어옵니다.
- 메시지가 질문·상의·설명 요청이면 코드를 바꾸지 말고 답만 하세요.
- 바꿔 달라는 요청이면 작업하세요. 작업 중 되물을 수는 없으니, 크게 애매하면 코드를 바꾸기 전에
  선택지를 짧게 제시하고 답을 기다리세요(다음 메시지로 옵니다). 사소한 애매함은 합리적으로 정하고 답변에 적으세요.
- 코드를 바꿔도 바로 배포되지 않습니다. 관리자가 화면의 [dev 배포]나 [대화 종료]를 누르면 워커가 그때까지의 커밋을
  한 번에 빌드해 개발 서버에 올리고, 확인 후 [운영 반영]을 누르면 운영에 나갑니다.

규칙:
- 지금 디렉터리(전용 git worktree, 브랜치 {branch})에서만 작업합니다. 다른 경로의 파일을 고치지 마세요.
- 코드를 바꿨으면 답하기 전에 반드시 git add + git commit 하세요(메시지는 한국어, 기존 커밋 스타일 `feat: ...`/`fix: ...`).
  push·배포·머지는 하지 마세요. 빌드와 개발 서버 배포는 워커가 합니다.
- 검증: 백엔드를 고쳤으면 `cd backend && ./gradlew compileJava compileTestJava` 와 관련 테스트,
  프론트를 고쳤으면 `cd frontend && npx tsc -b --noEmit` 가 통과해야 합니다. node_modules 는 이미 설치돼 있습니다.
- DB 스키마 변경이 필요하면 backend/src/main/resources/sql/ 에 기존 파일처럼 `riding_db`.`테이블` 로 스키마를 명시한
  **여러 번 실행해도 안전한** SQL(CREATE TABLE IF NOT EXISTS, ADD COLUMN IF NOT EXISTS 등)을 추가하세요.
  DROP/TRUNCATE/DELETE 등 데이터를 지우는 SQL 은 쓰지 마세요. 워커가 dev 배포 때 커밋된 SQL 파일을 dev DB 에 적용하고,
  운영 반영 승인 시 운영 DB 에도 적용합니다.
- 개발 DB 조회가 필요하면 `/mnt/200gb/devreq/bin/devdb.sh "SELECT ..."` (riding_db_dev, 개발용 복사본)를 쓰세요. 운영 DB 에는 접근할 수 없습니다.
- 비밀값(.env, 토큰, 비밀번호)을 코드·커밋·보고에 넣지 마세요.
- 요청이 위험하거나(데이터 삭제, 보안 약화 등) 이 저장소 범위를 벗어나면 코드를 바꾸지 말고 이유를 보고하세요.

마지막 답변은 한국어로, 채팅 말풍선에 마크다운으로 그대로 표시됩니다. 대화하듯 간결하게 쓰세요.
코드를 바꿨다면: 무엇을 바꿨는지 2~5줄 → [dev 배포] 후 개발 서버에서 확인할 방법(메뉴/화면 경로) → 가정·주의할 점(있으면).
"""

PIPELINE_SYSTEM_PROMPT = """\
당신은 data_pipeline(서버에서 cron 등으로 도는 파이썬 데이터 수집·가공 스크립트 모음) 저장소에서 앱 관리자(저장소 주인)와
앱 안의 채팅 화면으로 실시간 대화하며 개발 요청을 처리하는 작업자입니다. 대화가 열려 있는 동안 이 프로세스가 계속 살아 있고,
관리자의 다음 메시지가 바로 이어서 들어옵니다.
- 메시지가 질문·상의·설명 요청이면 코드를 바꾸지 말고 답만 하세요.
- 바꿔 달라는 요청이면 작업하세요. 작업 중 되물을 수는 없으니, 크게 애매하면 코드를 바꾸기 전에
  선택지를 짧게 제시하고 답을 기다리세요(다음 메시지로 옵니다). 사소한 애매함은 합리적으로 정하고 답변에 적으세요.
- 코드를 바꿔도 바로 반영되지 않습니다. 파이프라인은 개발 서버가 없어서 [dev 배포]는 워커가 문법 검사만 하고,
  [운영 반영]을 누르면 main 에 머지·push 한 뒤 서버의 실행용 체크아웃(/mnt/200gb/project/data_pipeline)을 갱신합니다.
  그 다음 실행(cron 등)부터 바뀐 코드가 돕니다.

규칙:
- 지금 디렉터리(전용 git worktree, 브랜치 {branch})에서만 작업합니다. 다른 경로의 파일을 고치지 마세요.
- 코드를 바꿨으면 답하기 전에 반드시 git add + git commit 하세요(메시지는 한국어, `feat: ...`/`fix: ...`).
  push·배포·머지는 하지 마세요.
- 스크립트를 실제로 실행하지 마세요(운영 DB·외부 API 에 쓸 수 있습니다). 이 worktree 에는 .env 가 없습니다.
  검증은 `python3 -m py_compile <파일>` 또는 `python3 -m compileall -q <폴더>` 로 하세요. pip install 도 하지 마세요.
- DB 구조·데이터를 봐야 하면 `/mnt/200gb/devreq/bin/devdb.sh "SELECT ..."` (riding_db_dev, 운영의 개발용 복사본)를 쓰세요.
- 비밀값(.env, 토큰, 비밀번호)을 코드·커밋·보고에 넣지 마세요. 다른 경로의 .env 를 읽지도 마세요.
- 요청이 위험하거나(데이터 삭제, 보안 약화 등) 이 저장소 범위를 벗어나면 코드를 바꾸지 말고 이유를 보고하세요.

마지막 답변은 한국어로, 채팅 말풍선에 마크다운으로 그대로 표시됩니다. 대화하듯 간결하게 쓰세요.
코드를 바꿨다면: 무엇을 바꿨는지 2~5줄 → 운영 반영 후 어떻게 확인하면 되는지 → 가정·주의할 점(있으면).
"""

# 대화의 작업 대상 저장소(dev_request.repo). worktree 는 WORK/wt/<prefix><root>.
REPOS = {
    "lifemetrics": {"path": REPO, "base": BASE, "main": "master", "prefix": "devreq-",
                    "prompt": SYSTEM_PROMPT, "tools": APP_TOOLS},
    "data_pipeline": {"path": PIPELINE_REPO, "base": PIPELINE_BASE, "main": "main", "prefix": "dp-devreq-",
                      "prompt": PIPELINE_SYSTEM_PROMPT, "tools": PIPELINE_TOOLS},
}


def repo_cfg(name: str | None) -> dict:
    cfg = REPOS.get(name or "lifemetrics")
    if cfg is None:
        raise ValueError(f"알 수 없는 저장소: {name}")
    return cfg


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


def api(method: str, path: str, body: dict | None = None, timeout: int = 30):
    req = urllib.request.Request(API + path, method=method,
                                 data=json.dumps(body).encode() if body is not None else None,
                                 headers={"Authorization": f"Bearer {TOKEN}", "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
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


ACTIVE_JOBS: set["Job"] = set()   # heartbeat 대상(진행 중)
ACTIVE_LOCK = threading.Lock()


class Job:
    """요청 하나를 처리하는 동안의 로그·heartbeat. req 가 None 이면(대화 준비 로그) 보고 없이 파일에만 남긴다."""

    def __init__(self, req: dict | None, status: str, logname: str | None = None):
        self.req = req or {}
        self.id = self.req.get("id")
        self.status = status
        self.lines: list[str] = []
        self.last_beat = 0.0
        self.lock = threading.Lock()
        WORK.joinpath("logs").mkdir(parents=True, exist_ok=True)
        self.logfile = WORK / "logs" / (logname or f"req-{self.id}.log")
        if self.id is not None:
            with ACTIVE_LOCK:
                ACTIVE_JOBS.add(self)

    def note(self, line: str, beat: bool = False) -> None:
        line = line.rstrip()
        if not line:
            return
        stamped = f"{datetime.now(KST):%H:%M:%S} {line}"
        with self.lock:
            self.lines.append(stamped)
            with self.logfile.open("a") as f:
                f.write(stamped + "\n")
        log(f"#{self.id or self.logfile.stem} {line[:200]}")
        if beat or time.time() - self.last_beat > HEARTBEAT_S:
            self.beat()

    def tail(self) -> str:
        with self.lock:
            return "\n".join(self.lines[-400:])

    def beat(self) -> None:
        self.last_beat = time.time()
        if self.id is not None:
            report(self.id, status=self.status, logTail=self.tail())

    def finish(self, **fields) -> None:
        """마지막 보고(DEV_READY/ANSWERED/FAILED/DONE). 이후 heartbeat 하지 않는다."""
        with ACTIVE_LOCK:
            ACTIVE_JOBS.discard(self)
        if self.id is not None:
            report(self.id, logTail=self.tail(), **fields)

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


def fetch_base(cfg: dict) -> None:
    if cfg["base"].startswith("origin/"):
        git("fetch", "-q", "origin", cfg["base"].split("/", 1)[1], cwd=cfg["path"])


def worktree_dir(branch: str, cfg: dict) -> Path:
    return WORK / "wt" / (cfg["prefix"] + branch.split("/", 1)[1])


def kst_now() -> str:
    return datetime.now(KST).strftime("%Y-%m-%d %H:%M:%S")


# ── 빌드·배포 ─────────────────────────────────────────────────────────────

GIT_LOCK = threading.RLock()  # 메인 저장소의 worktree 추가·정리·fetch 는 스레드끼리 겹치지 않게


def prepare_worktree(job: Job, branch: str, new: bool = False, repo: str | None = None) -> Path:
    cfg = repo_cfg(repo)
    with GIT_LOCK:
        wt = _prepare_worktree(job, branch, new, cfg)
    if cfg is REPOS["lifemetrics"] and not (wt / "frontend/node_modules").exists():
        job.run(["npm", "ci", "--no-audit", "--no-fund"], wt / "frontend", "npm ci", timeout=900)
    return wt


def _prepare_worktree(job: Job, branch: str, new: bool, cfg: dict) -> Path:
    wt = worktree_dir(branch, cfg)
    path, base = cfg["path"], cfg["base"]
    if new:
        fetch_base(cfg)
        if wt.exists():
            raise StepError(f"{wt} 가 이미 있습니다")
        git("worktree", "add", "-q", "-b", branch, str(wt), base, cwd=path)
        job.note(f"worktree 생성: {wt} ({branch} ← {base} {git('rev-parse', '--short', base, cwd=path)})")
    elif not wt.exists():
        git("worktree", "prune", cwd=path)
        if git("rev-parse", "--verify", "-q", f"refs/heads/{branch}", cwd=path, check=False):
            git("worktree", "add", "-q", str(wt), branch, cwd=path)
            job.note(f"worktree 복구: {wt}")
        else:  # 새 대화이거나 앞 요청이 브랜치를 만들기 전에 실패한 경우
            fetch_base(cfg)
            git("worktree", "add", "-q", "-b", branch, str(wt), base, cwd=path)
            job.note(f"worktree 새로 생성: {wt} ({branch} ← {base})")
    if cfg is REPOS["lifemetrics"]:
        # 지도 토큰 등 gitignore 된 프론트 설정
        src_env = REPO / "frontend/.env"
        if src_env.exists() and not (wt / "frontend/.env").exists():
            shutil.copy2(src_env, wt / "frontend/.env")
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


# ── Claude 상시 대화 ──────────────────────────────────────────────────────

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


def conv_state(root: int, state: str, reason: str | None = None) -> None:
    try:
        api("POST", f"/worker/conversations/{root}/state", {"state": state, "reason": reason})
    except Exception as e:  # noqa: BLE001
        log(f"대화 #{root} 상태 보고 실패: {e}")


class ConversationClosed(Exception):
    pass


CLOSE = object()  # 대화 큐에 넣으면 프로세스를 닫는다


class Conversation(threading.Thread):
    """대화 하나 = Claude 프로세스 하나(stdin 으로 메시지를 계속 넣는다). 메시지는 큐로 받아 하나씩 처리."""

    def __init__(self, root: int, session_id: str | None, repo: str | None):
        super().__init__(name=f"conv-{root}", daemon=True)
        self.root = root
        self.repo = repo or "lifemetrics"
        self.branch = f"devreq/{root}"
        self.session_id = session_id
        self.inbox: queue.Queue = queue.Queue()
        self.proc: subprocess.Popen | None = None
        self.wt: Path | None = None
        self.last_active = time.time()
        self.cost_seen = 0.0  # 프로세스 누적 비용(result.total_cost_usd) — 메시지별 비용은 차이로
        self.closed_by_user = False

    # 메인 스레드에서 부른다
    def submit(self, req: dict) -> None:
        self.inbox.put(req)

    def close(self) -> None:
        self.closed_by_user = True
        self.inbox.put(CLOSE)

    # ── 스레드 ──
    def run(self) -> None:
        reason = None
        prep = Job(None, "", logname=f"conv-{self.root}.log")
        try:
            self.start_proc(prep)
            conv_state(self.root, "LIVE")
            while True:
                try:
                    item = self.inbox.get(timeout=10)
                except queue.Empty:
                    if self.proc and self.proc.poll() is not None:
                        reason = "Claude 프로세스가 끝났습니다 — 다시 보내면 이어집니다"
                        break
                    if time.time() - self.last_active > IDLE_CLOSE_S:
                        reason = f"{IDLE_CLOSE_S // 60}분 동안 입력이 없어 자동 종료"
                        break
                    continue
                if item is CLOSE:
                    break
                self.handle(item)
        except ConversationClosed as e:
            reason = str(e)
        except Exception as e:  # noqa: BLE001
            reason = f"오류로 종료: {str(e).splitlines()[0][:150] if str(e) else type(e).__name__}"
            prep.note(traceback.format_exc())
        finally:
            self.stop_proc()
            unregister(self)
            # 남은 메시지는 실패 처리(다시 보내면 새 프로세스로 이어진다)
            while True:
                try:
                    item = self.inbox.get_nowait()
                except queue.Empty:
                    break
                if item is not CLOSE:
                    job = Job(item, "RUNNING")
                    job.note(f"❌ 대화가 닫혀 처리하지 못했습니다: {reason or '종료'}")
                    job.finish(status="FAILED", error=f"대화가 닫혀 처리하지 못했습니다: {reason or '종료'}")
            if reason and not self.closed_by_user:
                conv_state(self.root, "CLOSED", reason)
            log(f"대화 #{self.root} 닫음: {reason or '사용자 종료'}")

    def start_proc(self, job: Job) -> None:
        cfg = repo_cfg(self.repo)
        self.wt = prepare_worktree(job, self.branch, repo=self.repo)
        cmd = ["claude", "-p", "--input-format", "stream-json", "--output-format", "stream-json", "--verbose",
               "--permission-mode", "acceptEdits",
               "--allowedTools", *cfg["tools"], "--disallowedTools", *DISALLOWED_TOOLS,
               "--append-system-prompt", cfg["prompt"].format(branch=self.branch),
               "--max-budget-usd", CLAUDE_BUDGET_USD]
        if self.session_id:
            cmd += ["--resume", self.session_id]
        job.note(f"▶ Claude 대화 프로세스 시작 [{self.repo}]" + (f" (세션 이어서 {self.session_id[:8]})" if self.session_id else ""))
        self.proc = subprocess.Popen(cmd, cwd=self.wt, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                     stderr=subprocess.STDOUT, text=True, bufsize=1, env=child_env(),
                                     start_new_session=True)
        self.last_active = time.time()
        self.cost_seen = 0.0

    def stop_proc(self) -> None:
        p = self.proc
        if not p or p.poll() is not None:
            return
        try:
            assert p.stdin is not None
            p.stdin.close()  # 입력을 닫으면 스스로 끝난다
            p.wait(timeout=15)
        except Exception:  # noqa: BLE001
            try:
                os.killpg(p.pid, signal.SIGTERM)
                p.wait(timeout=10)
            except Exception:  # noqa: BLE001
                p.kill()

    def handle(self, req: dict) -> None:
        job = Job(req, "RUNNING")
        try:
            if not self.proc or self.proc.poll() is not None:
                self.start_proc(job)
            assert self.proc and self.proc.stdin and self.proc.stdout and self.wt
            wt = self.wt
            report(job.id, status="RUNNING", branch=self.branch)
            with GIT_LOCK:
                head_before = git("rev-parse", "HEAD", cwd=wt)
            job.note("▶ Claude 에 전달", beat=True)
            msg = {"type": "user", "parent_tool_use_id": None,
                   "message": {"role": "user",
                               "content": [{"type": "text", "text": f"[메시지 #{req['id']}]\n{req['body']}"}]}}
            self.proc.stdin.write(json.dumps(msg, ensure_ascii=False) + "\n")
            self.proc.stdin.flush()

            res = self.read_result(job)
            summary = (res.get("result") or "").strip()
            total = res.get("total_cost_usd")
            cost = None
            if isinstance(total, (int, float)):
                cost = round(total - self.cost_seen, 4) if total >= self.cost_seen else total
                self.cost_seen = total
            if res.get("session_id"):
                self.session_id = res["session_id"]
            report(job.id, sessionId=self.session_id, summary=summary or None, costUsd=cost)
            if res.get("is_error"):
                raise StepError(f"Claude 오류: {res.get('subtype')} {summary[:500]}")

            with GIT_LOCK:
                if git("status", "--porcelain", cwd=wt):  # Claude 가 커밋을 빠뜨렸으면 대신 커밋
                    git("add", "-A", cwd=wt)
                    git("commit", "-q", "-m", f"chore(devreq #{req['id']}): 작업 내용 커밋(자동)", cwd=wt)
                    job.note("커밋되지 않은 변경을 자동 커밋")
                head = git("rev-parse", "HEAD", cwd=wt)
                diff_stat = git("diff", "--stat", f"{head_before}..{head}", cwd=wt) if head != head_before else None
            if head == head_before:
                if not summary:
                    raise StepError("Claude 가 답 없이 끝났습니다.")
                job.note("💬 답변 완료")
                job.finish(status="ANSWERED")
            else:
                job.note(f"💾 커밋 {head[:7]}")
                job.finish(status="ANSWERED", commitSha=head, diffStat=diff_stat)
        except Exception as e:  # noqa: BLE001
            fail(job, e, notify=False)
            if self.proc and self.proc.poll() is not None:
                raise ConversationClosed("Claude 프로세스가 끝났습니다 — 다시 보내면 이어집니다")
        finally:
            self.last_active = time.time()

    def read_result(self, job: Job) -> dict:
        """다음 result 이벤트까지 읽는다. 시간 제한을 넘기면 프로세스를 죽인다."""
        assert self.proc and self.proc.stdout
        proc = self.proc
        timer = threading.Timer(CLAUDE_TIMEOUT_S, lambda: proc.poll() is None and proc.kill())
        timer.daemon = True
        timer.start()
        try:
            while True:
                line = proc.stdout.readline()
                if not line:
                    proc.wait()
                    if not timer.is_alive():
                        raise StepError(f"Claude 답이 {CLAUDE_TIMEOUT_S // 60}분을 넘어 중단했습니다.")
                    raise StepError(f"Claude 프로세스가 결과 없이 끝났습니다 (exit {proc.returncode})")
                line = line.strip()
                if not line:
                    continue
                try:
                    ev = json.loads(line)
                except json.JSONDecodeError:
                    job.note(line[:300])
                    continue
                if ev.get("type") == "system" and ev.get("session_id"):
                    self.session_id = ev["session_id"]
                for d in describe_event(ev):
                    job.note(d)
                if ev.get("type") == "result":
                    return ev
        finally:
            timer.cancel()


CONVERSATIONS: dict[int, Conversation] = {}
CONV_LOCK = threading.Lock()
RECENTLY_CLOSED: dict[int, float] = {}  # 워커가 스스로 닫은 대화 — 곧바로 다시 띄우지 않게


def unregister(conv: Conversation) -> None:
    with CONV_LOCK:
        if CONVERSATIONS.get(conv.root) is conv:
            del CONVERSATIONS[conv.root]
        if not conv.closed_by_user:  # 백엔드가 CLOSED 를 받기 전의 poll 응답으로 다시 띄우지 않게
            RECENTLY_CLOSED[conv.root] = time.time()


def ensure_conversation(root: int, session_id: str | None, repo: str | None) -> Conversation:
    with CONV_LOCK:
        conv = CONVERSATIONS.get(root)
        if conv and conv.is_alive():
            return conv
        conv = Conversation(root, session_id, repo)
        CONVERSATIONS[root] = conv
        RECENTLY_CLOSED.pop(root, None)
    log(f"대화 #{root} 열기" + (f" (세션 {session_id[:8]})" if session_id else ""))
    conv.start()
    return conv


def reconcile(wanted: list[dict]) -> None:
    """백엔드의 열린 대화(OPEN/LIVE)와 떠 있는 프로세스를 맞춘다."""
    want = {c["rootId"]: c for c in wanted}
    with CONV_LOCK:
        running = dict(CONVERSATIONS)
        recent = {r for r, t in RECENTLY_CLOSED.items() if time.time() - t < 15}
    for root, c in want.items():
        if root not in running and root not in recent:
            ensure_conversation(root, c.get("sessionId"), c.get("repo"))
    for root, conv in running.items():
        if root not in want:
            log(f"대화 #{root} 종료 요청")
            conv.close()


# ── 빌드가 필요한 일(한 번에 하나) ─────────────────────────────────────────

def handle_deploy(req: dict) -> None:
    job = Job(req, "RUNNING")
    root = req.get("rootId") or req["id"]
    branch = f"devreq/{root}"
    repo = req.get("repo") or "lifemetrics"
    base = repo_cfg(repo)["base"]
    try:
        report(job.id, status="RUNNING", branch=branch)
        wt = prepare_worktree(job, branch, repo=repo)
        with GIT_LOCK:
            if git("status", "--porcelain", cwd=wt):
                git("add", "-A", cwd=wt)
                git("commit", "-q", "-m", f"chore(devreq #{root}): 작업 내용 커밋(자동)", cwd=wt)
                job.note("커밋되지 않은 변경을 자동 커밋")
            if git("rev-list", "--count", f"{base}..HEAD", cwd=wt) == "0":
                raise StepError("올릴 커밋이 없습니다.")
            sha = git("rev-parse", "HEAD", cwd=wt)
            diff_stat = git("diff", "--stat", f"{base}...HEAD", cwd=wt)
            commits = git("log", "--format=- %s (%h)", f"{base}..HEAD", cwd=wt)
        report(job.id, commitSha=sha, diffStat=diff_stat)

        if repo == "data_pipeline":
            # 파이프라인은 개발 서버가 없다 — 문법 검사만
            job.run(["python3", "-m", "compileall", "-q", "-x", r"(^|/)\.venv/", "."], wt, "파이썬 문법 검사(compileall)",
                    timeout=300)
            job.note("✅ 검사 통과")
            job.finish(status="DEV_READY", summary="문법 검사를 통과했어요. data_pipeline 은 개발 서버가 없어서 실행은 해 보지 않았어요."
                                                   f" **운영 반영**을 누르면 main 에 머지하고 서버 체크아웃을 갱신해요.\n\n이 대화의 커밋:\n\n{commits}")
            return

        build(job, wt)
        apply_sql(job, wt, changed_sql(wt), "dev")
        install_artifacts(job, wt, DEV_DIR, "dev")
        deploy(job, wt, "dev")

        job.note("✅ 개발 서버 배포 완료")
        job.finish(status="DEV_READY", summary=f"개발 서버에 올렸어요. 이 대화의 커밋:\n\n{commits}")
        slack(f"✅ 개발 요청 #{root} 개발 서버 반영: {req['title']}\nhttp://{SERVER_IP}:8081 → 확인 후 앱에서 운영 반영")
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
        if (req.get("repo") or "lifemetrics") == "data_pipeline":
            promote_pipeline(job, req, branch)
            return
        wt = prepare_worktree(job, branch, new=False)
        with GIT_LOCK:
            if git("status", "--porcelain", cwd=wt):
                raise StepError("worktree 에 커밋되지 않은 변경이 있습니다")
            approved_sha = req.get("commitSha")
            if approved_sha and git("rev-parse", "HEAD", cwd=wt) != approved_sha:
                raise StepError("승인한 커밋과 브랜치 HEAD 가 다릅니다(그 사이 변경됨). [dev 배포]부터 다시 해 주세요.")

            git("fetch", "-q", "origin", "master", cwd=wt)
            p = subprocess.run(["git", "merge", "--no-edit", "-m",
                                f"Merge devreq #{req['id']}: {req['title']}", "origin/master"],
                               cwd=wt, capture_output=True, text=True)
            if p.returncode != 0:
                subprocess.run(["git", "merge", "--abort"], cwd=wt)
                raise StepError("origin/master 와 충돌합니다. 대화에서 충돌 해결을 지시해 주세요.\n" + p.stdout[-1500:])
            job.note(f"origin/master 반영: {git('log', '--oneline', '-1', cwd=wt)}")

        build(job, wt)
        apply_sql(job, wt, changed_sql(wt), "prod")
        # 운영에 올리는 코드는 항상 master 에 먼저 있어야 한다
        with GIT_LOCK:
            git("push", "-q", "origin", "HEAD:master", cwd=wt)
            job.note("origin/master push 완료")
            subprocess.run(["git", "fetch", "-q", "origin", "master:master"], cwd=REPO,
                           capture_output=True)  # master 가 어딘가 체크아웃돼 있으면 실패해도 무방
        install_artifacts(job, wt, PROD_DIR, "prod")
        deploy(job, wt, "prod")

        sha = git("rev-parse", "HEAD", cwd=wt)
        job.note("✅ 운영 배포 완료")
        job.finish(status="DONE", commitSha=sha)
        slack(f"🎉 개발 요청 #{req['id']} 운영 반영 완료: {req['title']}\nhttps://ride.tho881.me")
    except Exception as e:  # noqa: BLE001
        fail(job, e)


def promote_pipeline(job: Job, req: dict, branch: str) -> None:
    """data_pipeline 운영 반영: origin/main 머지 → push main → 서버 실행용 체크아웃을 fast-forward."""
    cfg = repo_cfg("data_pipeline")
    live, main = cfg["path"], cfg["main"]
    wt = prepare_worktree(job, branch, repo="data_pipeline")
    with GIT_LOCK:
        if git("status", "--porcelain", cwd=wt):
            raise StepError("worktree 에 커밋되지 않은 변경이 있습니다")
        approved_sha = req.get("commitSha")
        if approved_sha and git("rev-parse", "HEAD", cwd=wt) != approved_sha:
            raise StepError("승인한 커밋과 브랜치 HEAD 가 다릅니다(그 사이 변경됨). [dev 배포]부터 다시 해 주세요.")
        git("fetch", "-q", "origin", main, cwd=wt)
        p = subprocess.run(["git", "merge", "--no-edit", "-m", f"Merge devreq #{req['id']}: {req['title']}",
                            f"origin/{main}"], cwd=wt, capture_output=True, text=True)
        if p.returncode != 0:
            subprocess.run(["git", "merge", "--abort"], cwd=wt)
            raise StepError(f"origin/{main} 와 충돌합니다. 대화에서 충돌 해결을 지시해 주세요.\n" + p.stdout[-1500:])
    job.run(["python3", "-m", "compileall", "-q", "-x", r"(^|/)\.venv/", "."], wt, "파이썬 문법 검사(compileall)",
            timeout=300)
    with GIT_LOCK:
        git("push", "-q", "origin", f"HEAD:{main}", cwd=wt)
        job.note(f"origin/{main} push 완료")
        # 서버 실행용 체크아웃 갱신(fast-forward 만 — 서버에서 직접 고친 게 있으면 건드리지 않고 알린다)
        cur = git("rev-parse", "--abbrev-ref", "HEAD", cwd=live)
        if cur != main:
            raise StepError(f"push 는 됐지만 {live} 가 {main} 이 아닌 {cur} 브랜치라 갱신하지 않았습니다. 직접 확인해 주세요.")
        git("fetch", "-q", "origin", main, cwd=live)
        p = subprocess.run(["git", "merge", "--ff-only", f"origin/{main}"], cwd=live, capture_output=True, text=True)
        if p.returncode != 0:
            raise StepError(f"push 는 됐지만 {live} 를 fast-forward 하지 못했습니다(서버에서 직접 고친 내용이 있을 수 있음). "
                            f"직접 확인해 주세요.\n{(p.stdout + p.stderr)[-800:]}")
        sha = git("rev-parse", "HEAD", cwd=live)
    job.note(f"✅ 서버 체크아웃 갱신: {live} → {sha[:7]}")
    job.finish(status="DONE", commitSha=sha)
    slack(f"🎉 개발 요청 #{req['id']} data_pipeline 운영 반영 완료: {req['title']}")


def fail(job: Job, e: Exception, notify: bool = True) -> None:
    msg = str(e) if isinstance(e, StepError) else f"{type(e).__name__}: {e}\n{traceback.format_exc()[-1500:]}"
    job.note(f"❌ {msg.splitlines()[0] if msg else '실패'}")
    job.finish(status="FAILED", error=msg[:5000])
    if notify:
        slack(f"❌ 개발 요청 #{job.id} 실패: {job.req.get('title')}\n{msg[:300]}")


PIPELINE: threading.Thread | None = None


def start_pipeline(req: dict) -> None:
    global PIPELINE
    target = handle_promote if req["status"] == "PROMOTING" else handle_deploy
    PIPELINE = threading.Thread(target=target, args=(req,), name=f"pipeline-{req['id']}", daemon=True)
    PIPELINE.start()


def pipeline_busy() -> bool:
    return PIPELINE is not None and PIPELINE.is_alive()


def cleanup() -> None:
    """운영 반영됐거나 반려·취소된(그리고 대화가 닫힌) 브랜치의 worktree 를 지운다(node_modules·빌드 산출물 용량)."""
    root_dir = WORK / "wt"
    if not root_dir.exists() or pipeline_busy():
        return
    for wt in root_dir.iterdir():
        m = re.fullmatch(r"(dp-)?devreq-(\d+)", wt.name)
        if not m:
            continue
        repo_path = PIPELINE_REPO if m.group(1) else REPO
        root = int(m.group(2))
        with CONV_LOCK:
            if root in CONVERSATIONS:
                continue
        branch = f"devreq/{root}"
        try:
            st = api("POST", "/worker/branch-status", {"branch": branch})
        except Exception as e:  # noqa: BLE001
            log(f"cleanup 상태 조회 실패 {branch}: {e}")
            continue
        if st.get("conversation") in ("OPEN", "LIVE"):
            continue
        status = st.get("status", "")
        if status in ("DONE", "REJECTED", "CANCELLED"):
            with GIT_LOCK:
                subprocess.run(["git", "worktree", "remove", "--force", str(wt)], cwd=repo_path, capture_output=True)
                if status == "DONE":
                    subprocess.run(["git", "branch", "-D", branch], cwd=repo_path, capture_output=True)
            log(f"worktree 정리: {wt.name} ({status})")


def heartbeat_loop() -> None:
    """도구 하나가 오래 걸려도(빌드 등) 진행 중 작업이 멈춘 것으로 보이지 않게 주기적으로 알린다."""
    while True:
        time.sleep(HEARTBEAT_S)
        with ACTIVE_LOCK:
            jobs = list(ACTIVE_JOBS)
        for j in jobs:
            if time.time() - j.last_beat > HEARTBEAT_S:
                try:
                    j.beat()
                except Exception:  # noqa: BLE001
                    pass


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


def dispatch(req: dict) -> None:
    log(f"작업 #{req['id']} [{req.get('kind', 'MESSAGE')}/{req['status']}] {req['title']}")
    if req["status"] == "PROMOTING" or req.get("kind") == "DEPLOY":
        start_pipeline(req)
    else:
        root = req.get("rootId") or req["id"]
        ensure_conversation(root, req.get("sessionId"), req.get("repo")).submit(req)


def shutdown(*_args) -> None:
    log("종료 신호 — 대화 프로세스를 닫습니다")
    with CONV_LOCK:
        convs = list(CONVERSATIONS.values())
    for c in convs:
        if c.proc and c.proc.poll() is None:
            try:
                os.killpg(c.proc.pid, signal.SIGTERM)
            except Exception:  # noqa: BLE001
                pass
    sys.exit(0)


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
    signal.signal(signal.SIGTERM, shutdown)
    install_helpers()
    threading.Thread(target=heartbeat_loop, name="heartbeat", daemon=True).start()

    log(f"상시 실행 시작: api={API} base={BASE} promote={'off' if NO_PROMOTE else 'on'} idle={IDLE_CLOSE_S // 60}분")
    started = False
    version = -1
    last_cleanup = 0.0
    while True:
        try:
            if not started:  # 끊긴 작업 정리 + 열려 있던 대화를 다시 띄우게
                api("POST", "/worker/startup")
                started = True
            resp = api("POST", "/worker/poll", {"version": version, "waitMs": POLL_WAIT_MS},
                       timeout=POLL_WAIT_MS // 1000 + 15)
        except (urllib.error.URLError, TimeoutError, ConnectionError) as e:
            log(f"poll 실패(백엔드 응답 없음?): {e}")
            time.sleep(10)
            continue
        except Exception:  # noqa: BLE001
            log(traceback.format_exc())
            time.sleep(10)
            continue
        try:
            version = resp.get("version", version)
            if resp.get("job"):
                dispatch(resp["job"])
            reconcile(resp.get("conversations") or [])
            if not resp.get("job") and time.time() - last_cleanup > 600:
                cleanup()
                last_cleanup = time.time()
        except Exception:  # noqa: BLE001 — 한 건에서 예상 못 한 오류가 나도 서비스는 계속
            log(traceback.format_exc())
            time.sleep(2)


if __name__ == "__main__":
    sys.exit(main())
