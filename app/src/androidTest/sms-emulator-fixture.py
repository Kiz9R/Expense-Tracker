"""Send only synthetic multipart SMS after SmsReceiverDeliveryTest reports ready."""
import argparse
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--adb', required=True)
parser.add_argument('--serial', default='emulator-5554')
args = parser.parse_args()
if not args.serial.startswith('emulator-'):
    raise SystemExit('This fixture supports Android emulators only.')

# The emulator SMS sender accepts this alphanumeric origin and splits long text into GSM parts.
body = ('Rs 500 debited from A/c XX8391 to SYNTHETIC SHOP on 14-09-2026 UPI Ref 600000009191. '
        'Thank you for banking with SBI. This is a synthetic transaction message used only for automated testing.')
for text in [body, body, 'OTP 123456 transaction Rs 500 SYNTHETIC-OTP-IGNORE']:
    result = subprocess.run([args.adb, '-s', args.serial, 'emu', 'sms', 'send', 'AD-SBIINB', text],
                            check=True, capture_output=True, text=True)
    if 'KO:' in result.stdout + result.stderr:
        raise SystemExit(result.stdout + result.stderr)
print('Sent synthetic multipart, repeated, and OTP fixtures to ' + args.serial)
