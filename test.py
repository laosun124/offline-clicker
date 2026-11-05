#!/usr/bin/env python3
import pathlib,subprocess,tempfile
root=pathlib.Path(__file__).resolve().parent
with tempfile.TemporaryDirectory(prefix='offline-clicker-tests-') as out:
    production=root/'app/src/main/java/local/offline/clicker'
    subprocess.run(['javac','-encoding','UTF-8','-d',out,str(production/'Plan.java'),str(production/'Playback.java'),str(production/'Appearance.java'),str(production/'OverlayPosition.java'),str(root/'tests/PlaybackTest.java'),str(root/'tests/FeaturesTest.java')],check=True)
    subprocess.run(['java','-ea','-cp',out,'local.offline.clicker.PlaybackTest'],check=True)
    subprocess.run(['java','-ea','-cp',out,'local.offline.clicker.FeaturesTest'],check=True)
