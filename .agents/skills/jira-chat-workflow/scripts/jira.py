#!/usr/bin/env python3
"""Small Jira Cloud CLI for the spring-chat CHAT project."""

from __future__ import annotations

import argparse
import base64
import json
import os
import sys
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlencode
from urllib.request import Request, urlopen


DEFAULT_BASE_URL = "https://tongnamuu.atlassian.net"
DEFAULT_PROJECT_KEY = "CHAT"
DEFAULT_BOARD_ID = "34"
REPOSITORY_ROOT = Path(__file__).resolve().parents[4]


class JiraError(RuntimeError):
    pass


def load_dotenv(path: Path) -> None:
    if not path.is_file():
        return

    for line_number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[7:].lstrip()
        if "=" not in line:
            raise JiraError(f"Invalid .env entry at {path}:{line_number}")

        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip()
        if not key or not key.replace("_", "a").isalnum() or key[0].isdigit():
            raise JiraError(f"Invalid environment variable name at {path}:{line_number}")
        if len(value) >= 2 and value[0] == value[-1] and value[0] in {'"', "'"}:
            value = value[1:-1]
        os.environ.setdefault(key, value)


def print_json(value: Any) -> None:
    print(json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True))


def load_json_object(path: str | None) -> dict[str, Any]:
    if not path:
        return {}
    value = json.loads(Path(path).read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise JiraError(f"Expected a JSON object in {path}")
    return value


def read_body(text: str | None, file_path: str | None) -> str:
    if text is not None and file_path is not None:
        raise JiraError("Use either inline text or a file, not both")
    if file_path:
        return Path(file_path).read_text(encoding="utf-8")
    return text or ""


def text_to_adf(text: str) -> dict[str, Any]:
    content: list[dict[str, Any]] = []
    bullet_items: list[dict[str, Any]] = []

    def flush_bullets() -> None:
        if bullet_items:
            content.append({"type": "bulletList", "content": list(bullet_items)})
            bullet_items.clear()

    for raw_line in text.splitlines():
        line = raw_line.rstrip()
        if line.startswith("- "):
            bullet_items.append(
                {
                    "type": "listItem",
                    "content": [
                        {
                            "type": "paragraph",
                            "content": [{"type": "text", "text": line[2:]}],
                        }
                    ],
                }
            )
            continue

        flush_bullets()
        paragraph: dict[str, Any] = {"type": "paragraph"}
        if line:
            paragraph["content"] = [{"type": "text", "text": line}]
        content.append(paragraph)

    flush_bullets()
    if not content:
        content.append({"type": "paragraph"})
    return {"type": "doc", "version": 1, "content": content}


def adf_to_text(value: Any) -> str:
    if not isinstance(value, dict):
        return ""

    node_type = value.get("type")
    if node_type == "text":
        return str(value.get("text", ""))
    if node_type == "hardBreak":
        return "\n"

    rendered = "".join(adf_to_text(child) for child in value.get("content", []))
    if node_type in {"paragraph", "heading", "listItem"}:
        return rendered + "\n"
    return rendered


class JiraClient:
    def __init__(self, base_url: str, email: str, api_token: str) -> None:
        self.base_url = base_url.rstrip("/")
        auth = base64.b64encode(f"{email}:{api_token}".encode()).decode()
        self.headers = {
            "Accept": "application/json",
            "Authorization": f"Basic {auth}",
            "Content-Type": "application/json",
            "User-Agent": "spring-chat-jira-skill/1.0",
        }

    def request(
        self,
        method: str,
        path: str,
        payload: dict[str, Any] | None = None,
        params: dict[str, Any] | None = None,
    ) -> Any:
        url = f"{self.base_url}{path}"
        if params:
            url = f"{url}?{urlencode(params, doseq=True)}"
        data = None if payload is None else json.dumps(payload).encode("utf-8")
        request = Request(url, data=data, method=method, headers=self.headers)

        try:
            with urlopen(request, timeout=30) as response:
                body = response.read().decode("utf-8")
                return json.loads(body) if body else {}
        except HTTPError as error:
            body = error.read().decode("utf-8", errors="replace")
            try:
                details = json.dumps(json.loads(body), ensure_ascii=False)
            except json.JSONDecodeError:
                details = body
            raise JiraError(f"Jira HTTP {error.code} for {method} {path}: {details}") from error
        except URLError as error:
            raise JiraError(f"Could not reach Jira at {self.base_url}: {error.reason}") from error


def configured_client(args: argparse.Namespace) -> JiraClient:
    email = os.getenv("JIRA_EMAIL", "").strip()
    token = os.getenv("JIRA_API_TOKEN", "").strip()
    missing = [name for name, value in (("JIRA_EMAIL", email), ("JIRA_API_TOKEN", token)) if not value]
    if missing:
        raise JiraError(f"Missing required environment variable(s): {', '.join(missing)}")
    return JiraClient(args.base_url, email, token)


def project_id(client: JiraClient, project_key: str) -> str:
    project = client.request("GET", f"/rest/api/3/project/{quote(project_key)}")
    return str(project["id"])


def issue_types(client: JiraClient, project_key: str) -> list[dict[str, Any]]:
    response = client.request(
        "GET",
        "/rest/api/3/issuetype/project",
        params={"projectId": project_id(client, project_key)},
    )
    return response if isinstance(response, list) else response.get("values", [])


def resolve_issue_type(client: JiraClient, project_key: str, requested: str) -> dict[str, Any]:
    candidates = issue_types(client, project_key)
    for candidate in candidates:
        if str(candidate.get("id")) == requested or str(candidate.get("name", "")).casefold() == requested.casefold():
            return candidate
    available = ", ".join(f"{item.get('name')} ({item.get('id')})" for item in candidates)
    raise JiraError(f"Unknown issue type '{requested}'. Available: {available}")


def validate_issue_key(issue_key: str, project_key: str) -> str:
    normalized = issue_key.upper()
    if not normalized.startswith(f"{project_key.upper()}-"):
        raise JiraError(f"Issue {issue_key} is outside configured project {project_key}")
    return normalized


def command_config(args: argparse.Namespace) -> None:
    print_json(
        {
            "baseUrl": args.base_url,
            "boardId": args.board_id,
            "emailConfigured": bool(os.getenv("JIRA_EMAIL", "").strip()),
            "projectKey": args.project_key,
            "tokenConfigured": bool(os.getenv("JIRA_API_TOKEN", "").strip()),
        }
    )


def command_verify(args: argparse.Namespace) -> None:
    client = configured_client(args)
    myself = client.request("GET", "/rest/api/3/myself")
    print_json(
        {
            "accountId": myself.get("accountId"),
            "active": myself.get("active"),
            "baseUrl": client.base_url,
            "displayName": myself.get("displayName"),
            "emailAddress": myself.get("emailAddress"),
        }
    )


def command_project(args: argparse.Namespace) -> None:
    client = configured_client(args)
    print_json(client.request("GET", f"/rest/api/3/project/{quote(args.project_key)}"))


def command_issue_types(args: argparse.Namespace) -> None:
    client = configured_client(args)
    print_json(
        [
            {
                "description": item.get("description"),
                "id": item.get("id"),
                "name": item.get("name"),
                "subtask": item.get("subtask"),
            }
            for item in issue_types(client, args.project_key)
        ]
    )


def command_create(args: argparse.Namespace) -> None:
    client = configured_client(args)
    selected_type = resolve_issue_type(client, args.project_key, args.issue_type)
    description = read_body(args.description, args.description_file)
    fields: dict[str, Any] = {
        "project": {"key": args.project_key},
        "summary": args.summary,
        "issuetype": {"id": selected_type["id"]},
    }
    if description:
        fields["description"] = text_to_adf(description)
    if args.labels:
        fields["labels"] = args.labels
    fields.update(load_json_object(args.fields_file))

    created = client.request("POST", "/rest/api/3/issue", {"fields": fields})
    print_json(
        {
            "id": created.get("id"),
            "key": created.get("key"),
            "url": f"{client.base_url}/browse/{created.get('key')}",
        }
    )


def command_get(args: argparse.Namespace) -> None:
    client = configured_client(args)
    issue_key = validate_issue_key(args.issue_key, args.project_key)
    issue = client.request(
        "GET",
        f"/rest/api/3/issue/{quote(issue_key)}",
        params={
            "fields": "project,summary,description,status,issuetype,assignee,reporter,priority,labels,parent,subtasks,created,updated"
        },
    )
    issue["browseUrl"] = f"{client.base_url}/browse/{issue_key}"
    issue["descriptionText"] = adf_to_text(issue.get("fields", {}).get("description")).strip()
    if args.compact:
        fields = issue.get("fields", {})
        print_json(
            {
                "browseUrl": issue["browseUrl"],
                "key": issue.get("key"),
                "labels": fields.get("labels"),
                "project": fields.get("project", {}).get("key"),
                "status": fields.get("status", {}).get("name"),
                "statusId": fields.get("status", {}).get("id"),
                "summary": fields.get("summary"),
                "type": fields.get("issuetype", {}).get("name"),
            }
        )
        return
    print_json(issue)


def command_search(args: argparse.Namespace) -> None:
    client = configured_client(args)
    params: dict[str, Any] = {
        "jql": args.jql or f"project = {args.project_key} ORDER BY created DESC",
        "maxResults": args.max_results,
        "fields": "summary,status,issuetype,assignee,priority,labels,updated",
    }
    if args.reconcile_issue_ids:
        params["reconcileIssues"] = args.reconcile_issue_ids
    print_json(
        client.request(
            "GET",
            "/rest/api/3/search/jql",
            params=params,
        )
    )


def command_update(args: argparse.Namespace) -> None:
    client = configured_client(args)
    issue_key = validate_issue_key(args.issue_key, args.project_key)
    fields = load_json_object(args.fields_file)
    if args.summary is not None:
        fields["summary"] = args.summary
    description = read_body(args.description, args.description_file)
    if description:
        fields["description"] = text_to_adf(description)
    if not fields:
        raise JiraError("No update fields were provided")
    client.request("PUT", f"/rest/api/3/issue/{quote(issue_key)}", {"fields": fields})
    print_json({"key": issue_key, "updated": True, "url": f"{client.base_url}/browse/{issue_key}"})


def available_transitions(client: JiraClient, issue_key: str) -> list[dict[str, Any]]:
    response = client.request("GET", f"/rest/api/3/issue/{quote(issue_key)}/transitions")
    return response.get("transitions", [])


def command_transitions(args: argparse.Namespace) -> None:
    client = configured_client(args)
    issue_key = validate_issue_key(args.issue_key, args.project_key)
    print_json(
        [
            {
                "id": transition.get("id"),
                "name": transition.get("name"),
                "to": transition.get("to", {}).get("name"),
            }
            for transition in available_transitions(client, issue_key)
        ]
    )


def command_transition(args: argparse.Namespace) -> None:
    client = configured_client(args)
    issue_key = validate_issue_key(args.issue_key, args.project_key)
    transitions = available_transitions(client, issue_key)
    selected = next(
        (
            transition
            for transition in transitions
            if str(transition.get("id")) == args.target
            or str(transition.get("name", "")).casefold() == args.target.casefold()
            or str(transition.get("to", {}).get("name", "")).casefold() == args.target.casefold()
        ),
        None,
    )
    if selected is None:
        available = ", ".join(f"{item.get('name')} ({item.get('id')})" for item in transitions)
        raise JiraError(f"Transition '{args.target}' is not available. Available: {available}")
    client.request(
        "POST",
        f"/rest/api/3/issue/{quote(issue_key)}/transitions",
        {"transition": {"id": selected["id"]}},
    )
    print_json({"key": issue_key, "transition": selected.get("name"), "to": selected.get("to", {}).get("name")})


def command_assign(args: argparse.Namespace) -> None:
    client = configured_client(args)
    issue_key = validate_issue_key(args.issue_key, args.project_key)
    account_id = None if args.account_id.casefold() in {"none", "unassigned"} else args.account_id
    client.request("PUT", f"/rest/api/3/issue/{quote(issue_key)}/assignee", {"accountId": account_id})
    print_json({"accountId": account_id, "key": issue_key})


def command_comment(args: argparse.Namespace) -> None:
    client = configured_client(args)
    issue_key = validate_issue_key(args.issue_key, args.project_key)
    body = read_body(args.text, args.file)
    if not body.strip():
        raise JiraError("Comment body is empty")
    created = client.request(
        "POST",
        f"/rest/api/3/issue/{quote(issue_key)}/comment",
        {"body": text_to_adf(body)},
    )
    print_json({"commentId": created.get("id"), "key": issue_key, "url": f"{client.base_url}/browse/{issue_key}"})


def command_board(args: argparse.Namespace) -> None:
    client = configured_client(args)
    print_json(client.request("GET", f"/rest/agile/1.0/board/{quote(args.board_id)}"))


def command_board_config(args: argparse.Namespace) -> None:
    client = configured_client(args)
    print_json(client.request("GET", f"/rest/agile/1.0/board/{quote(args.board_id)}/configuration"))


def command_board_filter(args: argparse.Namespace) -> None:
    client = configured_client(args)
    configuration = client.request("GET", f"/rest/agile/1.0/board/{quote(args.board_id)}/configuration")
    filter_id = str(configuration["filter"]["id"])
    print_json(client.request("GET", f"/rest/api/3/filter/{quote(filter_id)}"))


def command_board_issues(args: argparse.Namespace) -> None:
    client = configured_client(args)
    print_json(
        client.request(
            "GET",
            f"/rest/agile/1.0/board/{quote(args.board_id)}/issue",
            params={"maxResults": args.max_results, "fields": "summary,status,issuetype,assignee,priority,labels,updated"},
        )
    )


def command_board_backlog(args: argparse.Namespace) -> None:
    client = configured_client(args)
    print_json(
        client.request(
            "GET",
            f"/rest/agile/1.0/board/{quote(args.board_id)}/backlog",
            params={"maxResults": args.max_results, "fields": "summary,status,issuetype,assignee,priority,labels,updated"},
        )
    )


def command_board_move(args: argparse.Namespace) -> None:
    client = configured_client(args)
    issue_keys = [validate_issue_key(issue_key, args.project_key) for issue_key in args.issue_keys]
    if len(issue_keys) > 50:
        raise JiraError("Jira allows moving at most 50 issues at once")
    client.request(
        "POST",
        f"/rest/agile/1.0/board/{quote(args.board_id)}/issue",
        {"issues": issue_keys},
    )
    print_json({"boardId": args.board_id, "issues": issue_keys, "moved": True})


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Jira Cloud CLI for the spring-chat CHAT project")
    parser.add_argument("--base-url", default=os.getenv("JIRA_BASE_URL", DEFAULT_BASE_URL))
    parser.add_argument("--project-key", default=os.getenv("JIRA_PROJECT_KEY", DEFAULT_PROJECT_KEY))
    parser.add_argument("--board-id", default=os.getenv("JIRA_BOARD_ID", DEFAULT_BOARD_ID))
    subparsers = parser.add_subparsers(dest="command", required=True)

    config = subparsers.add_parser("config", help="Show non-secret configuration")
    config.set_defaults(handler=command_config)

    verify = subparsers.add_parser("verify", help="Verify authentication with Jira")
    verify.set_defaults(handler=command_verify)

    project = subparsers.add_parser("project", help="Get the configured Jira project")
    project.set_defaults(handler=command_project)

    types = subparsers.add_parser("issue-types", help="List valid project issue types")
    types.set_defaults(handler=command_issue_types)

    create = subparsers.add_parser("create", help="Create a Jira issue")
    create.add_argument("summary")
    create.add_argument("--type", dest="issue_type", default="Task")
    create.add_argument("--description")
    create.add_argument("--description-file")
    create.add_argument("--label", dest="labels", action="append", default=[])
    create.add_argument("--fields-file", help="JSON object merged into Jira fields")
    create.set_defaults(handler=command_create)

    get = subparsers.add_parser("get", help="Get a Jira issue")
    get.add_argument("issue_key")
    get.add_argument("--compact", action="store_true")
    get.set_defaults(handler=command_get)

    search = subparsers.add_parser("search", help="Search Jira with JQL")
    search.add_argument("--jql")
    search.add_argument("--max-results", type=int, default=50)
    search.add_argument(
        "--reconcile-issue-id",
        dest="reconcile_issue_ids",
        action="append",
        default=[],
        help="Force a recently changed numeric issue ID into the search reconciliation set",
    )
    search.set_defaults(handler=command_search)

    update = subparsers.add_parser("update", help="Update issue fields")
    update.add_argument("issue_key")
    update.add_argument("--summary")
    update.add_argument("--description")
    update.add_argument("--description-file")
    update.add_argument("--fields-file", help="JSON object merged into Jira fields")
    update.set_defaults(handler=command_update)

    transitions = subparsers.add_parser("transitions", help="List available issue transitions")
    transitions.add_argument("issue_key")
    transitions.set_defaults(handler=command_transitions)

    transition = subparsers.add_parser("transition", help="Transition an issue by name or ID")
    transition.add_argument("issue_key")
    transition.add_argument("target")
    transition.set_defaults(handler=command_transition)

    assign = subparsers.add_parser("assign", help="Assign by Atlassian account ID or unassign")
    assign.add_argument("issue_key")
    assign.add_argument("account_id")
    assign.set_defaults(handler=command_assign)

    comment = subparsers.add_parser("comment", help="Add a Jira comment")
    comment.add_argument("issue_key")
    comment.add_argument("text", nargs="?")
    comment.add_argument("--file")
    comment.set_defaults(handler=command_comment)

    board = subparsers.add_parser("board", help="Get configured Jira board metadata")
    board.set_defaults(handler=command_board)

    board_config = subparsers.add_parser("board-config", help="Get board columns and filter configuration")
    board_config.set_defaults(handler=command_board_config)

    board_filter = subparsers.add_parser("board-filter", help="Get the configured board's saved filter")
    board_filter.set_defaults(handler=command_board_filter)

    board_issues = subparsers.add_parser("board-issues", help="List issues on the configured board")
    board_issues.add_argument("--max-results", type=int, default=50)
    board_issues.set_defaults(handler=command_board_issues)

    board_backlog = subparsers.add_parser("board-backlog", help="List issues in the configured board backlog")
    board_backlog.add_argument("--max-results", type=int, default=50)
    board_backlog.set_defaults(handler=command_board_backlog)

    board_move = subparsers.add_parser("board-move", help="Move backlog issues onto the configured board")
    board_move.add_argument("issue_keys", nargs="+")
    board_move.set_defaults(handler=command_board_move)

    return parser


def main() -> int:
    try:
        load_dotenv(REPOSITORY_ROOT / ".env")
        parser = build_parser()
        args = parser.parse_args()
        args.handler(args)
        return 0
    except (JiraError, json.JSONDecodeError, OSError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
