import asyncio

try:
    from app.evals.demo_dataset import ASK_DEMO_CASES, MESSAGE_DEMO_CASES
    from app.services.agent_service import build_rag_answer, draft_message_with_agent
except ImportError:
    from evals.demo_dataset import ASK_DEMO_CASES, MESSAGE_DEMO_CASES
    from services.agent_service import build_rag_answer, draft_message_with_agent


async def eval_ask():
    print("[ASK EVAL]")
    for idx, case in enumerate(ASK_DEMO_CASES, start=1):
        # 注意：这里只评 LLM 输出逻辑，检索数据需要实际接入
        result = await build_rag_answer(case["question"], records=[])
        passed = any(k.lower() in result.answer.lower() for k in case["expected_keywords"])
        print(f"- case {idx}: {'PASS' if passed else 'FAIL'} | {result.answer}")


async def eval_message():
    print("[MESSAGE EVAL]")
    for idx, case in enumerate(MESSAGE_DEMO_CASES, start=1):
        draft = await draft_message_with_agent(
            scenario=case["scenario"],
            tone=case["tone"],
            latest_user_message=case["latest_user_message"],
            conversation_brief="最近会话较短。",
            agent_role=None,
            custom_prompt=None,
        )
        forbid = case.get("must_not_include", [])
        require_any = case.get("must_include_any", [])
        ok = True
        if forbid and any(w in draft for w in forbid):
            ok = False
        if require_any and not any(w in draft for w in require_any):
            ok = False
        print(f"- case {idx}: {'PASS' if ok else 'FAIL'} | {draft}")


async def main():
    await eval_ask()
    await eval_message()


if __name__ == "__main__":
    asyncio.run(main())
