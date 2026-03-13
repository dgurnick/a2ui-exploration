# Demo Recording Script for Banking App
# Automates UI interactions via ADB while recording
# Screen: 1080x2400

$adb = "C:\Users\denni\AppData\Local\Android\Sdk\platform-tools\adb.exe"

function Tap($x, $y, $desc = "") {
    if ($desc) { Write-Host "  [$desc] Tap ($x, $y)" } else { Write-Host "  Tap ($x, $y)" }
    & $adb shell input tap $x $y
    Start-Sleep -Milliseconds 1500
}

function Swipe($x1, $y1, $x2, $y2, $duration = 500) {
    Write-Host "  Swipe ($x1,$y1) -> ($x2,$y2)"
    & $adb shell input swipe $x1 $y1 $x2 $y2 $duration
    Start-Sleep -Milliseconds 800
}

function TypeText($text) {
    Write-Host "  Type: $text"
    & $adb shell input text $text
    Start-Sleep -Milliseconds 500
}

function Wait($seconds) {
    Write-Host "  Waiting $seconds seconds..."
    Start-Sleep -Seconds $seconds
}

# Fixed coordinates from UI dump (Screen: 1080x2400)
# Greeting buttons:
$BTN_ATM_Y = 534           # "Where is the nearest ATM?" 
$BTN_BALANCE_Y = 676       # "What is my account balance?"
$BTN_OFFERS_Y = 818        # "What offers do you have for me?"
$BTN_CENTER_X = 540        # All buttons centered

# Input area:
$INPUT_X = 424
$INPUT_Y = 2242
$SEND_X = 943
$SEND_Y = 2244

# Restart button (top right):
$RESTART_X = 1007
$RESTART_Y = 217

Write-Host "=== Banking App Demo Recording Script ==="
Write-Host ""

# Clear any existing recording
& $adb shell rm -f /sdcard/demo.mp4

# Force stop and restart app for clean state
Write-Host "Launching Banking app fresh..."
& $adb shell am force-stop com.dgurnick.banking
Start-Sleep -Seconds 1
& $adb shell am start -n com.dgurnick.banking/.ui.MainActivity
Start-Sleep -Seconds 3

# Start recording
Write-Host "Starting screen recording (90 seconds)..."
Start-Process -FilePath $adb -ArgumentList "shell screenrecord --time-limit 90 --size 720x1600 /sdcard/demo.mp4" -NoNewWindow
Start-Sleep -Seconds 2

Write-Host ""
Write-Host "=== PART 1: ATM/Map Feature ==="
Wait 2

# Tap "Where is the nearest ATM?" button
Tap $BTN_CENTER_X $BTN_ATM_Y "ATM button"
Wait 4

# Scroll to see the map better
Swipe 540 1400 540 800
Wait 3

# Interact with map - zoom/pan
Swipe 540 1200 540 1000
Wait 2

Write-Host ""
Write-Host "=== PART 2: Restart ==="

# Tap restart button
Tap $RESTART_X $RESTART_Y "Restart button"
Wait 3

Write-Host ""
Write-Host "=== PART 3: Loan Offers ==="

# Tap "What offers do you have for me?" button
Tap $BTN_CENTER_X $BTN_OFFERS_Y "Offers button"
Wait 4

# Scroll to see loan options
Swipe 540 1400 540 900
Wait 2

# Tap on a loan type (Personal Loan - usually first option around y=600-800 after message)
Tap 540 750 "Personal Loan"
Wait 3

Write-Host ""
Write-Host "=== PART 4: Enter Loan Amount ==="

# Type loan amount
Tap $INPUT_X $INPUT_Y "Input field"
Wait 1
TypeText "25000"
Tap $SEND_X $SEND_Y "Send"
Wait 4

# Scroll to see purpose options
Swipe 540 1600 540 1000
Wait 2

# Select purpose (Home Improvement - middle option)
Tap 540 1200 "Home Improvement"
Wait 4

Write-Host ""
Write-Host "=== PART 5: View and Accept Offer ==="

# Scroll to see the full offer
Swipe 540 1600 540 700
Wait 3

# Accept the offer (first button, usually left side)
Tap 350 1700 "Accept offer"
Wait 4

# Scroll to see confirmation
Swipe 540 1400 540 800
Wait 3

Write-Host ""
Write-Host "=== PART 6: Goodbye ==="

# Type goodbye
Tap $INPUT_X $INPUT_Y "Input field"
Wait 1
TypeText "goodbye"
Tap $SEND_X $SEND_Y "Send"
Wait 5

Write-Host ""
Write-Host "=== Demo Complete ==="

# Stop recording
Write-Host "Stopping recording..."
& $adb shell pkill -2 screenrecord 2>$null
Start-Sleep -Seconds 3

# Pull the recording
Write-Host "Pulling demo video..."
& $adb pull /sdcard/demo.mp4 C:\Users\denni\src\a2ui\demo.mp4

$size = (Get-Item C:\Users\denni\src\a2ui\demo.mp4).Length / 1MB
Write-Host ""
Write-Host "Demo saved: $([math]::Round($size, 2)) MB"
Write-Host "Done!"
