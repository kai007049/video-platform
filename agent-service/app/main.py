"""Agent Service 启动入口。"""

import logging
from fastapi import FastAPI

from app.api.routes.tasks import router as tasks_router, agent_router


logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
)

app = FastAPI(title="Agent Service")
app.include_router(tasks_router)
app.include_router(agent_router)


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("main:app", host="0.0.0.0", port=8001, reload=False)
