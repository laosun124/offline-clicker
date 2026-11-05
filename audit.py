#!/usr/bin/env python3
"""Inspect the actual APK; do not run it or access any network/device."""
import argparse,hashlib,json,pathlib,re,struct,subprocess,sys,zipfile
root=pathlib.Path(__file__).resolve().parent
version=dict(line.strip().split('=',1) for line in (root/'version.properties').read_text().splitlines() if '=' in line)
p=argparse.ArgumentParser();p.add_argument('--sdk',required=True);p.add_argument('--apk',default=str(root/'build'/('offline-clicker-'+version['versionName']+'.apk')));args=p.parse_args()
apk=pathlib.Path(args.apk);tools=pathlib.Path(args.sdk)/'build-tools/34.0.0';out=root/'verification';out.mkdir(exist_ok=True)
def capture(*cmd):return subprocess.check_output([str(x) for x in cmd],text=True)
manifest=capture(tools/'aapt','dump','xmltree',apk,'AndroidManifest.xml')
badging=capture(tools/'aapt','dump','badging',apk)
access=capture(tools/'aapt','dump','xmltree',apk,'res/xml/accessibility_service.xml')
signature=capture(tools/'apksigner','verify','--verbose','--print-certs',apk)
capture(tools/'zipalign','-c','4',apk)
(out/'apk-manifest.txt').write_text(manifest);(out/'apk-badging.txt').write_text(badging);(out/'accessibility-config.txt').write_text(access);(out/'signature.txt').write_text(signature)
assert 'uses-permission' not in manifest,'Unexpected Android permission'
assert 'local.offline.clicker' in badging
assert "versionName='"+version['versionName']+"'" in badging
assert "versionCode='"+version['versionCode']+"'" in badging
assert 'canRetrieveWindowContent' in access and re.search(r'canRetrieveWindowContent[^\n]*0x0\b',access)
assert re.search(r'canPerformGestures[^\n]*0xffffffff',access)
assert 'canTakeScreenshot' not in access
assert re.search(r'canRequestFilterKeyEvents[^\n]*0x0\b',access)
assert 'flagRequestFilterKeyEvents' not in access
assert re.search(r'usesCleartextTraffic[^\n]*0x0\b',manifest)
descriptors=[];classes=[];inventory=[];all_strings=[]
with zipfile.ZipFile(apk) as z:
    assert z.testzip() is None
    for info in z.infolist():
        inventory.append({'path':info.filename,'size':info.file_size})
        assert not info.filename.endswith(('.so','.jar')),'Unexpected native library/plugin'
        if not re.fullmatch(r'classes\d*\.dex',info.filename):continue
        d=z.read(info.filename)
        def u(off):return struct.unpack_from('<I',d,off)[0]
        def string(off):
            while d[off]&128:off+=1
            off+=1;return d[off:d.index(b'\0',off)].decode('utf-8','replace')
        strings=[string(u(u(60)+4*i)) for i in range(u(56))]
        all_strings+=strings
        types=[strings[u(u(68)+4*i)] for i in range(u(64))]
        descriptors+=types
        classes += [types[u(u(100)+32*i)] for i in range(u(96))]
    assert not any(n.startswith(('Lcom/alienchao/','Lcom/bytedance/','Lcom/umeng/','Lcom/qq/','Lrikka/')) for n in descriptors)
    assert all(n.startswith('Llocal/offline/clicker/') for n in classes),'Unexpected third-party implementation'
    blocked=('Ljava/net/','Landroid/webkit/','Landroid/telephony/','Ldalvik/system/','Landroid/location/','Landroid/media/MediaRecorder','Landroid/media/projection/')
    assert not any(n.startswith(blocked) for n in descriptors),'Network/screen-capture/dynamic-loader dependency found'
    assert not any(s in all_strings for s in ('takeScreenshot','getRootInActiveWindow','getInstalledPackages','getInstalledApplications','getDeviceId','getImei','getPrimaryClip','getMacAddress','exec'))
test_output=capture(sys.executable,root/'test.py')
(out/'playback-tests.txt').write_text(test_output)
test_count=sum(int(m) for m in re.findall(r'passed: (\d+) checks',test_output))
result={'app':'离线连点器','version':version['versionName'],'version_code':int(version['versionCode']),'package':'local.offline.clicker','apk_bytes':apk.stat().st_size,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'uses_permissions':[],'accessibility_gestures':True,'accessibility_window_content':False,'screenshot_capability':False,'network_api_references':False,'original_ad_sdk_references':False,'native_libraries_or_embedded_jars':False,'app_defined_dex_classes':len(classes),'zip_integrity':True,'zip_alignment':True,'signature_verified':True,'device_or_emulator_functional_test':False,'pure_java_test_checks':test_count}
(out/'audit.json').write_text(json.dumps(result,ensure_ascii=False,indent=2))
(out/'apk-inventory.json').write_text(json.dumps(inventory,ensure_ascii=False,indent=2))
(out/'dex-classes.txt').write_text('\n'.join(classes)+'\n')
print(json.dumps(result,ensure_ascii=False,indent=2))
