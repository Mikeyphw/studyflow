#!/usr/bin/env python3
"""Verify only-additive SHA-256 Gradle dependency metadata generated on hosted CI."""
from __future__ import annotations
import argparse, hashlib, json, re, sys
from pathlib import Path
import xml.etree.ElementTree as ET
SHA=re.compile(r'[0-9a-f]{64}\Z')
TARGET=('com.android.tools.build','aapt2','9.3.1-15703166','aapt2-9.3.1-15703166.pom')
class Refused(ValueError):pass
def need(b,m):
 if not b:raise Refused(m)
def digest(b):return hashlib.sha256(b).hexdigest()
def metadata(path):
 need(path.is_file() and not path.is_symlink(),'missing or linked Gradle XML')
 b=path.read_bytes();need(256<=len(b)<=16*1024*1024,'unbounded XML')
 upper=b.upper();need(b'<!DOCTYPE' not in upper and b'<!ENTITY' not in upper,'unsafe XML entity')
 root=ET.fromstring(b);need(root.tag.rsplit('}',1)[-1]=='verification-metadata','not Gradle XML')
 items={}
 for component in root.iter():
  if component.tag.rsplit('}',1)[-1]!='component':continue
  g,n,v=(component.get(k) for k in ('group','name','version'))
  need(all((g,n,v)),'invalid component')
  for artifact in component:
   if artifact.tag.rsplit('}',1)[-1]!='artifact':continue
   name=artifact.get('name');need(bool(name),'unnamed artifact')
   sha=[child.get('value','') for child in artifact if child.tag.rsplit('}',1)[-1]=='sha256']
   need(len(sha)==1 and SHA.fullmatch(sha[0]) is not None,'unverified/duplicate SHA-256')
   key=(g,n,v,name)
   need(key not in items,'duplicate artifact identity')
   items[key]=sha[0]
 need(bool(items),'no verified artifacts')
 return items,b
def compare(base,candidate):
 before,base_bytes=metadata(base);after,candidate_bytes=metadata(candidate)
 for key,digest_value in before.items():need(after.get(key)==digest_value,'existing digest changed or removed: '+':'.join(key))
 need(TARGET in after,'hosted Gradle candidate lacks official aapt2 POM')
 need(TARGET not in before,'aapt2 POM already verified: no import necessary')
 added={key:value for key,value in after.items() if key not in before}
 need(1<=len(added)<=500,'unexpected number of new artifacts')
 return {'kind':'studyflow.sfdt15.hosted-android-dependency-metadata','status':'ADDITIVE_GRADLE_SHA256_CANDIDATE_NOT_RELEASE','old_bytes_sha256':digest(base_bytes),'new_bytes_sha256':digest(candidate_bytes),'old_count':len(before),'new_count':len(after),'added_count':len(added),'aapt2_pom_sha256':after[TARGET],'additions':[{'coordinate':':'.join(key),'sha256':val} for key,val in sorted(added.items())], 'release_qualified':False}
def main():
 p=argparse.ArgumentParser();p.add_argument('--base',type=Path,required=True);p.add_argument('--candidate',type=Path,required=True);p.add_argument('--out',type=Path)
 a=p.parse_args()
 try:
  r=compare(a.base,a.candidate);data=json.dumps(r,indent=2,sort_keys=True)+'\n'
  if a.out:
   a.out.parent.mkdir(parents=True,exist_ok=True);need(not a.out.exists(),'receipt already exists');a.out.write_text(data)
  print('SFDT15_ANDROID_METADATA_ADDITIVE_VERIFIED',r['added_count'],r['aapt2_pom_sha256'])
  return 0
 except (Refused,ValueError,ET.ParseError,OSError) as e:print('SFDT15_METADATA_REFUSED:',e,file=sys.stderr);return 2
if __name__=='__main__':raise SystemExit(main())
