#!/usr/bin/env python3
"""Build the app using only Python, JDK 17 and the official Android SDK tools.

No downloads, Gradle daemon, third-party libraries or global configuration changes.
Signing material stays in signing/ and is never included in the source archive.
"""
import argparse
import hashlib
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent
VERSION = dict(line.strip().split('=', 1) for line in (ROOT/'version.properties').read_text().splitlines() if '=' in line)

def run(*args):
    subprocess.run([str(a) for a in args], check=True, cwd=ROOT)

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--sdk', default=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT'))
    parser.add_argument('--build-tools', default='34.0.0')
    parser.add_argument('--android',default='34')
    options=parser.parse_args()
    sdk=Path(options.sdk) if options.sdk else Path.home()/'Library/Android/sdk'
    android=sdk/'platforms'/('android-'+options.android)/'android.jar'
    tools=sdk/'build-tools'/options.build_tools
    if not android.exists() or not tools.exists():
        parser.error('Supply --sdk PATH containing platform android-34 and build-tools 34.0.0')
    java_bin=Path(os.environ['JAVA_HOME'])/'bin' if os.environ.get('JAVA_HOME') else None
    def java_tool(name):
        value=java_bin/name if java_bin else shutil.which(name)
        if not value:raise SystemExit('JDK 17 tool missing: '+name)
        return value
    build=ROOT/'build';build.mkdir(exist_ok=True)
    for name in ('classes','generated','dex'):
        folder=build/name
        if folder.exists():shutil.rmtree(folder)
        folder.mkdir()
    manifest=ET.parse(ROOT/'app/src/main/AndroidManifest.xml')
    manifest.getroot().set('package','local.offline.clicker')
    ET.register_namespace('android','http://schemas.android.com/apk/res/android')
    manifest.write(build/'AndroidManifest.xml',encoding='utf-8',xml_declaration=True)
    run(tools/'aapt2','compile','--dir',ROOT/'app/src/main/res','-o',build/'compiled.zip')
    run(tools/'aapt2','link','-o',build/'resources.apk','-I',android,'--manifest',build/'AndroidManifest.xml','--java',build/'generated','--min-sdk-version','24','--target-sdk-version','34','--version-code',VERSION['versionCode'],'--version-name',VERSION['versionName'],build/'compiled.zip')
    sources=sorted((ROOT/'app/src/main/java').rglob('*.java'))+sorted((build/'generated').rglob('*.java'))
    # Every source belongs to this small project. Compile argument paths are quoted
    # for javac's argument-file syntax, including a workspace path with spaces.
    (build/'sources.txt').write_text('\n'.join('"'+str(p).replace('\\','\\\\').replace('"','\\"')+'"' for p in sources),encoding='utf-8')
    run(java_tool('javac'),'-encoding','UTF-8','--release','8','-classpath',android,'-d',build/'classes','@'+str(build/'sources.txt'))
    with zipfile.ZipFile(build/'classes.jar','w',zipfile.ZIP_DEFLATED) as z:
        for p in sorted((build/'classes').rglob('*.class')):z.write(p,p.relative_to(build/'classes'))
    run(tools/'d8','--release','--min-api','24','--lib',android,'--output',build/'dex',build/'classes.jar')
    shutil.copyfile(build/'resources.apk',build/'unsigned.apk')
    with zipfile.ZipFile(build/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:
        for p in sorted((build/'dex').glob('*.dex')):z.write(p,p.name)
    run(tools/'zipalign','-f','4',build/'unsigned.apk',build/'aligned.apk')
    signing=ROOT/'signing';signing.mkdir(exist_ok=True);os.chmod(signing,0o700)
    password=signing/'password.txt';keystore=signing/'offline-clicker.jks'
    if not keystore.exists():
        password.write_text(secrets.token_urlsafe(32),encoding='utf-8');os.chmod(password,0o600)
        run(java_tool('keytool'),'-genkeypair','-keystore',keystore,'-alias','offline-clicker','-storetype','JKS','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=Offline Clicker Local Build','-storepass:file',password,'-keypass:file',password)
        os.chmod(keystore,0o600)
    if not password.exists():raise SystemExit('Existing keystore needs signing/password.txt; do not replace the key for updates.')
    apk=build/('offline-clicker-'+VERSION['versionName']+'.apk')
    run(tools/'apksigner','sign','--ks',keystore,'--ks-key-alias','offline-clicker','--ks-pass','file:'+str(password),'--out',apk,build/'aligned.apk')
    run(tools/'apksigner','verify','--verbose','--print-certs',apk)
    run(tools/'zipalign','-c','4',apk)
    digest=hashlib.sha256(apk.read_bytes()).hexdigest()
    (build/'SHA256SUMS.txt').write_text(digest+'  '+apk.name+'\n',encoding='utf-8')
    print('Built:',apk)
    print('SHA-256:',digest)

if __name__=='__main__':main()
