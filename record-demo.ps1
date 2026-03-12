# Demo Recording Script for Banking App
# Automates UI interactions via ADB while recording
# Screen: 1080x2400, Recording: 720x1600

$adb = "C:\Users\denni\AppData\Local\Android\Sdk\platform-tools\adb.exe"

function Tap($x, $y) {
    Write-Host "  Tap ($x, $y)"
    & $adb shell input tap $x $y
    Start-Sleep -Milliseconds 1000
}

function Swipe($x1, $y1, $x2, $y2, $duration = 400) {
    Write-Host "  Swipe ($x1,$y1) -> ($x2,$y2)"
    & $adb shell input swipe $x1 $y1 $x2 $y2 $duration
    Start-Sleep -Milliseconds 600
}

function TypeText($text) {
    Write-Host "  Type: $text"
    & $adb shell input text $text
    Start-Sleep -Milliseconds 400
}

function Wait($seconds) {
    Write-Host "  Waiting $seconds seconds..."
    Start-Sleep -Seconds $seconds
}

function DumpUI() {
    & $adb shell uiautomator dump /sdcard/ui.xml 2>$null
    $xml = & $adb shell cat /sdcard/ui.xml 2>$null
    return $xml
}

function FindAndTap($searchText) {
    Write-Host "  Looking for: $searchText"
    $xml = DumpUI
    if ($xml -match "text=`"[^`"]*$searchText[^`"]*`"[^>]*bounds=`"\[(\d+),(\d+)\]\[(\d+),(\d+)\]`"") {
        $x = [int](([int]$matches[1] + [int]$matches[3]) / 2)
        $y = [int](([int]$matches[2] + [int]$matches[4]) / 2)
        Write-Host "  Found at ($x, $y)"
        Tap $x $y
        return $true
    }
    Write-Host "  Not found!"
    return $false
}

# UI coordinates from dump:
# - Input field: [32,2169][817,2316] -> center (424, 2242)
# - Send button: [838,2181][1048,2307] -> center (943, 2244)
# - Scrollable area: [32,300][1048,2148]

$INPUT_X = 424
$INPUT_Y = 2242
$SEND_X = 943
$SEND_Y = 2244

Write-Host "=== Banking App Demo Recording Script ==="
Write-Host ""

# Clear any existing recording
& $adb shell rm -f /sdcard/demo.mp4

# Start recording in background
Write-Host "Starting screen recording (120 seconds)..."
Start-Process -FilePath $adb -ArgumentList "shell screenrecord --time-limit 120 --size 720x1600 /sdcard/demo.mp4" -NoNewWindow

Wait 2

# Force stop and restart app for clean state
Write-Host "Launching Banking app fresh..."
& $adb shell am force-stop com.dgurnick.banking
Wait 1
& $adb shell am start -n com.dgurnick.banking/.ui.MainActivity
Wait 4

Write-Host ""
Write-Host "=== PART 1: Initial Greeting ==="
Wait 3

# Let user see the greeting
Swipe 540 1200 540 900
Wait 2

Write-Host ""
Write-Host "=== PART 2: Find Nearby Branches (Map) ==="

# Try to find and tap the branches button, or type it
if (-not (FindAndTap "branches")) {
    Write-Host "Typing request for branches..."
    Tap $INPUT_X $INPUT_Y
    Wait 1
    TypeText "find%snearby%sbranches"
    Wait 1
    Tap $SEND_X $SEND_Y
}
Wait 5

# Scroll to see the map
Write-Host "Scrolling to see map..."
Swipe 540 1400 540 700
Wait 3

# Let viewer see the map
Wait 3

Write-Host ""
Write-Host "=== PART 3: Start Over ==="

# Type start over
Tap $INPUT_X $INPUT_Y
Wait 1
TypeText "start%sover"
Wait 1
Tap $SEND_X $SEND_Y
Wait 3

Write-Host ""
Write-Host "=== PART 4: Loan Application ==="

# After restart, tap the loan option
Wait 2
if (-not (FindAndTap "loan")) {
    if (-not (FindAndTap "Explore")) {
        # Try tapping first button area
        Tap 300 1600
    }
}
Wait 3

# Select Personal Loan
if (-not (FindAndTap "Personal")) {
    Tap 300 1600
}
Wait 3

# Enter amount
Write-Host "Entering loan amount..."
Tap $INPUT_X $INPUT_Y
Wait 1
TypeText "20000"
Wait 1
Tap $SEND_X $SEND_Y
Wait 3

# Select purpose - Home Improvement
if (-not (FindAndTap "Home")) {
    Tap 540 1600
}
Wait 4

# Scroll to see the loan offer
Swipe 540 1600 540 800
Wait 2

# Accept the offer
Write-Host "Accepting loan offer..."
if (-not (FindAndTap "Accept")) {
    Tap 300 1800
}
Wait 4

Write-Host ""
Write-Host "=== PART 5: Goodbye ==="

# Scroll to see confirmation
Swipe 540 1400 540 900
Wait 2

# Say goodbye
Tap $INPUT_X $INPUT_Y
Wait 1
TypeText "goodbye"
Wait 1
Tap $SEND_X $SEND_Y
Wait 4

# Let goodbye message show
Wait 3

Write-Host ""
Write-Host "=== Demo Complete ==="

# Stop recording
Write-Host "Stopping recording..."
& $adb shell pkill -2 screenrecord 2>$null
Wait 3

# Pull the recording
Write-Host "Pulling demo video..."
& $adb pull /sdcard/demo.mp4 C:\Users\denni\src\a2ui\demo.mp4

Write-Host ""
Write-Host "Demo recording saved to demo.mp4"
Write-Host "Done!"
