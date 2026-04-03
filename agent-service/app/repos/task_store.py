from typing import Dict, Any
from uuid import uuid4
import time


# 内存任务存储（单机开发场景使用）。
TASKS: Dict[str, Dict[str, Any]] = {}


def create_task() -> str:
    """创建任务并初始化为 running 状态。"""
    task_id = str(uuid4())
    TASKS[task_id] = {
        "status": "running",
        "created_at": time.time(),
        "result": None,
        "error": None,
    }
    return task_id


def complete_task(task_id: str, result: Dict[str, Any]) -> None:
    """标记任务成功并写入结果。"""
    task = TASKS.get(task_id)
    if not task:
        return
    task["status"] = "success"
    task["result"] = result


def fail_task(task_id: str, error: str) -> None:
    """标记任务失败并记录错误信息。"""
    task = TASKS.get(task_id)
    if not task:
        return
    task["status"] = "failed"
    task["error"] = error


def get_task(task_id: str) -> Dict[str, Any] | None:
    """按任务 ID 查询任务状态。"""
    return TASKS.get(task_id)
