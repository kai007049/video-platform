from typing import Dict, Any
from uuid import uuid4
import time


TASKS: Dict[str, Dict[str, Any]] = {}


def create_task() -> str:
    task_id = str(uuid4())
    TASKS[task_id] = {
        "status": "running",
        "created_at": time.time(),
        "result": None,
        "error": None,
    }
    return task_id


def complete_task(task_id: str, result: Dict[str, Any]) -> None:
    task = TASKS.get(task_id)
    if not task:
        return
    task["status"] = "success"
    task["result"] = result


def fail_task(task_id: str, error: str) -> None:
    task = TASKS.get(task_id)
    if not task:
        return
    task["status"] = "failed"
    task["error"] = error


def get_task(task_id: str) -> Dict[str, Any] | None:
    return TASKS.get(task_id)
