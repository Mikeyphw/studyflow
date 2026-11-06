"""Pure-Python, fail-closed Gradle metadata integrity regression tests."""
from __future__ import annotations
import sys, tempfile, unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import sfdt15_android_metadata as s
BASE_COORD=('g','lib','1.0','lib.jar')
def make(entries):
 body=''.join('<component group="{}" name="{}" version="{}"><artifact name="{}"><sha256 value="{}"/></artifact></component>'.format(*k,v) for k,v in entries)
 return '<?xml version="1.0"?><verification-metadata><components>'+body+'</components></verification-metadata>'+' '*350
class Policy(unittest.TestCase):
 def compare(self,old,after):
  with tempfile.TemporaryDirectory() as d:
   p=Path(d)/'old.xml';q=Path(d)/'new.xml';p.write_text(make(old));q.write_text(make(after));return s.compare(p,q)
 def test_only_additive_real_coordinate_admitted(self):
  r=self.compare([(BASE_COORD,'a'*64)],[(BASE_COORD,'a'*64),(s.TARGET,'b'*64)])
  self.assertEqual(r['added_count'],1);self.assertEqual(r['aapt2_pom_sha256'],'b'*64)
 def test_changed_old_sha_is_refused(self):
  with self.assertRaises(s.Refused):self.compare([(BASE_COORD,'a'*64)],[(BASE_COORD,'c'*64),(s.TARGET,'b'*64)])
 def test_removed_old_entry_is_refused(self):
  with self.assertRaises(s.Refused):self.compare([(BASE_COORD,'a'*64)],[(s.TARGET,'b'*64)])
 def test_missing_actual_pom_is_refused(self):
  with self.assertRaises(s.Refused):self.compare([(BASE_COORD,'a'*64)],[(BASE_COORD,'a'*64),(('g','extra','1','extra.pom'),'b'*64)])
 def test_duplicate_artifact_rejected(self):
  with self.assertRaises(s.Refused):self.compare([(BASE_COORD,'a'*64)],[(BASE_COORD,'a'*64),(s.TARGET,'b'*64),(s.TARGET,'b'*64)])
 def test_existing_aapt2_requires_no_second_import(self):
  with self.assertRaises(s.Refused):self.compare([(BASE_COORD,'a'*64),(s.TARGET,'b'*64)],[(BASE_COORD,'a'*64),(s.TARGET,'b'*64)])
 def test_doctype_rejected(self):
  with tempfile.TemporaryDirectory() as d:
   p=Path(d)/'x.xml';p.write_text('<!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]>'+make([(BASE_COORD,'a'*64)]))
   with self.assertRaises(s.Refused):s.metadata(p)
 def test_invalid_sha_rejected(self):
  with self.assertRaises(s.Refused):self.compare([(BASE_COORD,'z'*64)],[(BASE_COORD,'a'*64),(s.TARGET,'b'*64)])
if __name__=='__main__':unittest.main(verbosity=2)
