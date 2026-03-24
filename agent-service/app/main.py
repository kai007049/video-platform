from fastapi import FastAPI

try:
    from app.api.routes.tasks import router as tasks_router
except ImportError:
    from api.routes.tasks import router as tasks_router


app = FastAPI(title="Agent Service")
app.include_router(tasks_router)


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("main:app", host="0.0.0.0", port=8001, reload=False)
