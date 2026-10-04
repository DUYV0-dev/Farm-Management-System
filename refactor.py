import os
import shutil
import glob

base_dir = r"d:\Farm Management System N02\Farm-Management-System\src\main\java\com\farmmanagement\backend"

moves = {
    "Api.java": "common",
    "ApiErrors.java": "common",
    "DomainException.java": "common",
    "Input.java": "common",
    "RequestIdFilter.java": "common",
    
    "SecurityConfig.java": "config",
    "SeedAccount.java": "config",
    
    "FarmController.java": "farm",
    "FarmService.java": "farm",
    "FarmStore.java": "farm",
    
    "ManagementController.java": "management",
    "ManagementPages.java": "management",
    "ManagementService.java": "management",
    "ManagementStore.java": "management",
}

# Create dirs
for pkg in ["common", "config", "farm", "management", "auth", "inventory"]:
    os.makedirs(os.path.join(base_dir, pkg), exist_ok=True)

# Move files
for file, pkg in moves.items():
    src = os.path.join(base_dir, "auth", file)
    dst = os.path.join(base_dir, pkg, file)
    if os.path.exists(src):
        shutil.move(src, dst)

# Update packages and imports in all java files
java_files = glob.glob(os.path.join(base_dir, "**", "*.java"), recursive=True)

class_to_pkg = {
    "Api": "com.farmmanagement.backend.common",
    "ApiErrors": "com.farmmanagement.backend.common",
    "DomainException": "com.farmmanagement.backend.common",
    "Input": "com.farmmanagement.backend.common",
    "RequestIdFilter": "com.farmmanagement.backend.common",
    "SecurityConfig": "com.farmmanagement.backend.config",
    "SeedAccount": "com.farmmanagement.backend.config",
    "FarmController": "com.farmmanagement.backend.farm",
    "FarmService": "com.farmmanagement.backend.farm",
    "FarmStore": "com.farmmanagement.backend.farm",
    "ManagementController": "com.farmmanagement.backend.management",
    "ManagementPages": "com.farmmanagement.backend.management",
    "ManagementService": "com.farmmanagement.backend.management",
    "ManagementStore": "com.farmmanagement.backend.management",
    "AuthController": "com.farmmanagement.backend.auth",
    "AuthService": "com.farmmanagement.backend.auth",
    "AuthStore": "com.farmmanagement.backend.auth",
    "InventoryController": "com.farmmanagement.backend.inventory",
    "InventoryService": "com.farmmanagement.backend.inventory",
    "InventoryStore": "com.farmmanagement.backend.inventory",
}

for filepath in java_files:
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    # Determine current package
    rel_path = os.path.relpath(filepath, base_dir)
    pkg_name = os.path.dirname(rel_path).replace(os.sep, '.')
    if not pkg_name:
        pkg_name = ""
    else:
        pkg_name = "." + pkg_name

    new_package = "package com.farmmanagement.backend" + pkg_name + ";"
    
    import re
    # Replace package
    content = re.sub(r'^package\s+.*?;', new_package, content, count=1, flags=re.MULTILINE)

    # We need to add imports if we use classes from other packages that are now separated.
    # A simple way is just to add all possible imports that are used in the file but are from different packages.
    
    needed_imports = set()
    for cls, pkg in class_to_pkg.items():
        if pkg != "com.farmmanagement.backend" + pkg_name:
            # If the class name appears in the file as a word
            if re.search(r'\b' + cls + r'\b', content):
                needed_imports.add(f"import {pkg}.{cls};")

    # Replace old imports pointing to auth that are now somewhere else
    content = re.sub(r'import\s+com\.farmmanagement\.backend\.auth\.(Api|ApiErrors|DomainException|Input|RequestIdFilter|SecurityConfig|SeedAccount|FarmController|FarmService|FarmStore|ManagementController|ManagementPages|ManagementService|ManagementStore);\s*\n?', '', content)

    # Insert new imports after the package declaration
    if needed_imports:
        import_block = "\n".join(needed_imports) + "\n"
        content = re.sub(r'^(package\s+.*?;)', r'\1\n\n' + import_block, content, count=1, flags=re.MULTILINE)
        
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)
    
print("Refactoring completed.")
