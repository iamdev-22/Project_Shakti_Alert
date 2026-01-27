"""
Fix all server URLs in Android app
"""
import os
import re

# Files to fix
files = [
    r"d:\project sakti\android_shakti\app\src\main\java\com\example\shaktialert\MapFragment.kt",
    r"d:\project sakti\android_shakti\app\src\main\java\com\example\shaktialert\LoginActivity.kt",
    r"d:\project sakti\android_shakti\app\src\main\java\com\example\shaktialert\LiveMapFragment.kt",
    r"d:\project sakti\android_shakti\app\src\main\java\com\example\shaktialert\ForgotPasswordActivity.kt",
    r"d:\project sakti\android_shakti\app\src\main\java\com\example\shaktialert\EnhancedLocationFragment.kt",
    r"d:\project sakti\android_shakti\app\src\main\java\com\example\shaktialert\CirclesFragment.kt",
    r"d:\project sakti\android_shakti\app\src\main\java\com\example\shaktialert\CircleChatFragment.kt",
    r"d:\project sakti\android_shakti\app\src\main\java\com\example\shaktialert\AlertService.kt",
]

total_replacements = 0

for file_path in files:
    if not os.path.exists(file_path):
        print(f"Skipping {os.path.basename(file_path)} - not found")
        continue
    
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Replace all instances
    new_content = content.replace('http://10.0.2.2:5000', 'http://192.168.1.42:5000')
    
    # Count replacements
    replacements = content.count('http://10.0.2.2:5000')
    
    if replacements > 0:
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(new_content)
        print(f"✅ {os.path.basename(file_path)}: {replacements} replacements")
        total_replacements += replacements
    else:
        print(f"⏭️  {os.path.basename(file_path)}: Already fixed")

print(f"\n🎉 Total: {total_replacements} server URLs fixed!")
