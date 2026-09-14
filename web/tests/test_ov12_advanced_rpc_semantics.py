from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[2]
WEB = ROOT / "web"


class Ov12AdvancedRpcSemanticsTests(unittest.TestCase):
    def test_ov12_node_semantics_runner(self):
        result = subprocess.run(
            ["node", "tests/ov12_advanced_rpc_semantics_runner.mjs"],
            cwd=WEB,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
        )
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertIn("OV12_ADVANCED_RPC_SEMANTICS: PASS", result.stdout)

    def test_ov12_source_contract_tokens(self):
        source = (WEB / "src" / "sflp_link.ts").read_text(encoding="utf-8")
        for token in [
            "fencing_token_mismatch",
            "executor_mismatch",
            "content_hash_mismatch",
            "out_of_order_chunk",
            "missing_content_bytes",
            "inner_target_mismatch",
            "StudyFlowWebHashing",
        ]:
            self.assertIn(token, source)


if __name__ == "__main__":
    unittest.main()
