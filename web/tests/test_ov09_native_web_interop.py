from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[2]
WEB = ROOT / "web"


class Ov09NativeWebInteropTests(unittest.TestCase):
    def test_encrypted_pairing_and_authenticated_rpc_runner(self):
        result = subprocess.run(
            ["node", "tests/ov09_interop_runner.mjs"],
            cwd=WEB,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            check=False,
        )
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertIn("OV09_WEB_NATIVE_INTEROP: PASS", result.stdout)

    def test_web_pairing_contract_uses_encrypted_grants_and_opaque_key_ids(self):
        source = (WEB / "src" / "sflp_link.ts").read_text(encoding="utf-8")
        for token in [
            "clientKeyAgreementPublicKeyHex",
            "hostKeyAgreementPublicKeyHex",
            "initializationVectorHex",
            "encryptedTrustSecretHex",
            "decryptPairingSecret",
            "trustKeyId",
            "StudyFlowWebCryptoTrustKeyStore",
            "link.reconnect_proof",
        ]:
            self.assertIn(token, source)
        self.assertNotIn("trustSecret:string", source)

    def test_interop_fixture_never_models_plaintext_secret_persistence(self):
        runner = (WEB / "tests" / "ov09_interop_runner.mjs").read_text(encoding="utf-8")
        self.assertNotIn("trustSecret:", runner)
        self.assertNotIn("record.trustSecret", runner)
        self.assertIn("encryptedTrustSecretHex", runner)
        self.assertIn("localStorage must not persist the raw host trust secret", runner)


if __name__ == "__main__":
    unittest.main()
