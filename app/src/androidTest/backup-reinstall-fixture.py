"""Destructive ONLY to the named disposable emulator's synthetic tracker installation.

Requires --confirm-disposable and verifies an emulator property. Never targets a physical phone.
The host retains encrypted synthetic archives and hashes in build/backup-reinstall-proof.
"""
import argparse
import pathlib
import subprocess

parser=argparse.ArgumentParser()
parser.add_argument('--adb',required=True)
parser.add_argument('--serial',default='emulator-5554')
parser.add_argument('--confirm-disposable',action='store_true',required=True)
args=parser.parse_args()
if not args.serial.startswith('emulator-'):
    raise SystemExit('Only a disposable emulator is permitted.')
root=pathlib.Path(__file__).resolve().parents[3]
out=root/'build'/'backup-reinstall-proof'
out.mkdir(parents=True,exist_ok=True)
package='com.kiz9r.expense_tracker'
runner=package+'.test/androidx.test.runner.AndroidJUnitRunner'

def adb(*command):
    return subprocess.check_output([args.adb,'-s',args.serial,*command],stderr=subprocess.STDOUT)

if adb('shell','getprop','ro.kernel.qemu').strip()!=b'1' and adb('shell','getprop','ro.boot.qemu').strip()!=b'1':
    raise SystemExit('Emulator verification failed.')

def phase(name):
    result=adb('shell','am','instrument','-w','-e','class',package+'.BackupReinstallTest','-e','backup_reinstall',name,runner)
    (out/(name+'.txt')).write_bytes(result)
    if b'OK (1 test)' not in result and b'OK (1 tests)' not in result:
        raise SystemExit('Reinstall phase failed; inspect '+str(out/(name+'.txt')))

phase('prepare')
names=['synthetic-reinstall.etbackup','synthetic-reinstall.digest','synthetic-reinstall.keyhash']
for name in names:
    data=adb('exec-out','run-as',package,'cat','files/'+name)
    if name.endswith('.etbackup') and not data.startswith(b'ETBACK01'):
        raise SystemExit('Synthetic backup was not authenticated-envelope format; refusing uninstall.')
    if not name.endswith('.etbackup') and len(data)!=64:
        raise SystemExit('Missing comparison hash; refusing uninstall.')
    (out/name).write_bytes(data)

# All commands remain bound to the verified emulator. No physical device or real fixture is used.
adb('uninstall',package)
adb('install','-r',str(root/'app/build/outputs/apk/debug/app-debug.apk'))
adb('install','-r',str(root/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'))
adb('shell','run-as',package,'mkdir','-p','files')
for name in names:
    temporary='/data/local/tmp/expense-tracker-'+name
    adb('push',str(out/name),temporary)
    adb('shell','run-as',package,'cp',temporary,'files/'+name)
    adb('shell','rm',temporary)
phase('verify')
print('PASS: actual emulator uninstall/reinstall; new device key; all persistent records restored; tracking disabled.')
