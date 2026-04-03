"""离线评估脚本：快速验证问答与私信草稿能力。"""

import asyncio

from app.evals.demo_dataset import ASK_DEMO_CASES, MESSAGE_DEMO_CASES
from app.services.agent_service import build_rag_answer, draft_message_with_agent


async def eval_ask() -> None:
    """评估问答输出是否覆盖预期关键词。"""
    print("[ASK EVAL]")
    for idx, case in enumerate(ASK_DEMO_CASES, start=1):
        # 这里只验证回答逻辑，检索数据在该示例中用空列表代替。
        result = await build_rag_answer(case["question"], records=[])
        passed = any(keyword in result.answer.lower() for keyword in case["expected_keywords"])
        print(f"- case {idx}: {'PASS' if passed else 'FAIL'} | {result.answer}")


async def eval_message() -> None:
    """评估私信草稿是否满足禁词/必含词约束。"""
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
        if forbid and any(word in draft for word in forbid):
            ok = False
        if require_any and not any(word in draft for word in require_any):
            ok = False
        print(f"- case {idx}: {'PASS' if ok else 'FAIL'} | {draft}")


async def main() -> None:
    """评估脚本主入口。"""
    await eval_ask()
    await eval_message()


if __name__ == "__main__":
    asyncio.run(main())

