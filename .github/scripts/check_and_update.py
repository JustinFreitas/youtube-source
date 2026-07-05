import os
import re
import urllib.request
import json
import sys
import subprocess
from datetime import date

# Paths
ROOT_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SETTINGS_PATH = os.path.join(ROOT_DIR, "settings.gradle.kts")
REPORT_PATH = os.path.join(ROOT_DIR, "build", "dependencyUpdates", "report.json")

def parse_settings_catalog():
    if not os.path.exists(SETTINGS_PATH):
        print(f"Error: settings.gradle.kts not found at {SETTINGS_PATH}", file=sys.stderr)
        sys.exit(1)

    with open(SETTINGS_PATH, "r", encoding="utf-8") as f:
        content = f.read()

    # Parse version("key", "val")
    version_defs = {}
    for m in re.finditer(r'version\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)', content):
        version_defs[m.group(1)] = m.group(2)

    # Parse library("key", "group", "name").versionRef("versionRef")
    # or library("key", "group", "name").version("versionVal")
    libraries = {}
    for m in re.finditer(r'library\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)(?:\.versionRef\(\s*"([^"]+)"\s*\)|\.version\(\s*"([^"]+)"\s*\))', content):
        libraries[m.group(1)] = {
            "group": m.group(2),
            "name": m.group(3),
            "version_ref": m.group(4) if m.group(4) else None,
            "version_val": m.group(5) if m.group(5) else None
        }

    # Parse plugin("key", "id").versionRef("versionRef")
    # or plugin("key", "id").version("versionVal")
    plugins = {}
    for m in re.finditer(r'plugin\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)(?:\.versionRef\(\s*"([^"]+)"\s*\)|\.version\(\s*"([^"]+)"\s*\))', content):
        plugins[m.group(1)] = {
            "id": m.group(2),
            "version_ref": m.group(3) if m.group(3) else None,
            "version_val": m.group(4) if m.group(4) else None
        }

    return version_defs, libraries, plugins

def update_settings_file(updates):
    with open(SETTINGS_PATH, "r", encoding="utf-8") as f:
        lines = f.readlines()

    updated_lines = []
    
    # regexes for lines
    version_pattern = re.compile(r'^(\s*version\(\s*"([^"]+)"\s*,\s*")([^"]+)("\s*\))')
    library_val_pattern = re.compile(r'^(\s*library\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)\.version\(\s*")([^"]+)("\s*\))')
    plugin_val_pattern = re.compile(r'^(\s*plugin\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)\.version\(\s*")([^"]+)("\s*\))')

    updated = False
    for line in lines:
        # 1. Version line
        m = version_pattern.match(line)
        if m:
            prefix, key, current_val, suffix = m.groups()
            if key in updates:
                new_val = updates[key]
                line = f"{prefix}{new_val}{suffix}\n"
                updated = True
            updated_lines.append(line)
            continue

        # 2. Library inline version line
        m = library_val_pattern.match(line)
        if m:
            prefix, key, group, name, current_val, suffix = m.groups()
            if key in updates:
                new_val = updates[key]
                line = f"{prefix}{new_val}{suffix}\n"
                updated = True
            updated_lines.append(line)
            continue

        # 3. Plugin inline version line
        m = plugin_val_pattern.match(line)
        if m:
            prefix, key, plugin_id, current_val, suffix = m.groups()
            if key in updates:
                new_val = updates[key]
                line = f"{prefix}{new_val}{suffix}\n"
                updated = True
            updated_lines.append(line)
            continue

        updated_lines.append(line)

    if updated:
        with open(SETTINGS_PATH, "w", encoding="utf-8") as f:
            f.writelines(updated_lines)
        print("Successfully updated settings.gradle.kts.")
    return updated

def get_next_version():
    try:
        tags_output = subprocess.check_output(["git", "tag"], text=True).strip().split("\n")
    except Exception as e:
        print(f"Error running git tag: {e}", file=sys.stderr)
        tags_output = []

    pattern = re.compile(r'^v?(\d+\.\d+\.\d+)_(\d+)$')
    highest_suffix = -1
    base_version = "1.18.1"
    
    for tag in tags_output:
        match = pattern.match(tag)
        if match:
            b_ver, suffix_str = match.groups()
            suffix = int(suffix_str)
            if suffix > highest_suffix:
                highest_suffix = suffix
                base_version = b_ver
                
    if highest_suffix != -1:
        return f"v{base_version}_{highest_suffix + 1}"
    else:
        return "v1.18.1_4"

def update_changelog(next_version, changes):
    changelog_path = os.path.join(ROOT_DIR, "CHANGELOG.md")
    if not os.path.exists(changelog_path):
        print(f"CHANGELOG.md not found at {changelog_path}, skipping.", file=sys.stderr)
        return
    
    with open(changelog_path, "r", encoding="utf-8") as f:
        content = f.read()

    today = date.today().isoformat()
    new_section = f"## [{next_version.lstrip('v')}] - {today}\n"
    for change in changes:
        new_section += f"* {change}\n"
    new_section += "\n"

    pattern = re.compile(r'(# Change Log\n+)')
    match = pattern.search(content)
    if match:
        insert_pos = match.end()
        updated_content = content[:insert_pos] + new_section + content[insert_pos:]
        with open(changelog_path, "w", encoding="utf-8") as f:
            f.write(updated_content)
        print(f"Updated CHANGELOG.md with version {next_version}")
    else:
        print("Could not find '# Change Log' header in CHANGELOG.md", file=sys.stderr)

def main():
    if "--update-changelog" in sys.argv:
        changes_path = os.path.join(ROOT_DIR, "build", "dependency_changes.json")
        if os.path.exists(changes_path):
            with open(changes_path, "r", encoding="utf-8") as f:
                data = json.load(f)
            update_changelog(data["next_version"], data["changes"])
            sys.exit(0)
        else:
            print("Error: build/dependency_changes.json not found", file=sys.stderr)
            sys.exit(1)

    version_defs, libraries, plugins = parse_settings_catalog()
    
    updates = {}
    changelog_entries = []
    gradle_updated = False

    # Check dependencyUpdates report.json
    if os.path.exists(REPORT_PATH):
        print("Reading Gradle dependency report...")
        with open(REPORT_PATH, "r", encoding="utf-8") as f:
            report = json.load(f)

        # Libraries
        for dep in report.get("outdated", {}).get("dependencies", []):
            group = dep.get("group")
            name = dep.get("name")
            current_val = dep.get("version")
            available = dep.get("available", {})
            latest = available.get("release") or available.get("milestone")
            
            if latest and latest != current_val:
                found_key = None
                for lib_key, lib_info in libraries.items():
                    if lib_info["group"] == group and lib_info["name"] == name:
                        found_key = lib_key
                        break
                
                if found_key:
                    lib_info = libraries[found_key]
                    if lib_info["version_ref"]:
                        ref = lib_info["version_ref"]
                        if "lavaplayer" in ref:
                            continue  # Managed by release bump dispatches
                        updates[ref] = latest
                        changelog_entries.append(f"Updated library reference `{ref}` to `{latest}` (was `{version_defs.get(ref)}`)")
                    else:
                        updates[found_key] = latest
                        changelog_entries.append(f"Updated library `{group}:{name}` to `{latest}` (was `{current_val}`)")
                    gradle_updated = True

        # Plugins
        for dep in report.get("outdated", {}).get("dependencies", []):
            group = dep.get("group")
            name = dep.get("name")
            current_val = dep.get("version")
            available = dep.get("available", {})
            latest = available.get("release") or available.get("milestone")
            
            if name.endswith(".gradle.plugin"):
                plugin_id = name[:-14]
                found_key = None
                for pl_key, pl_info in plugins.items():
                    if pl_info["id"] == plugin_id:
                        found_key = pl_key
                        break
                
                if found_key:
                    pl_info = plugins[found_key]
                    if pl_info["version_ref"]:
                        ref = pl_info["version_ref"]
                        updates[ref] = latest
                        changelog_entries.append(f"Updated plugin reference `{ref}` to `{latest}` (was `{version_defs.get(ref)}`)")
                    else:
                        updates[found_key] = latest
                        changelog_entries.append(f"Updated plugin `{plugin_id}` to `{latest}` (was `{current_val}`)")
                    gradle_updated = True

        # Gradle Wrapper update checking
        gradle_info = report.get("gradle", {})
        if gradle_info.get("current", {}).get("isUpdateAvailable"):
            latest_gradle = gradle_info["current"]["version"]
            running_gradle = gradle_info["running"]["version"]
            print(f"Gradle wrapper update available: {running_gradle} -> {latest_gradle}")
            updates["gradle-wrapper"] = latest_gradle
            changelog_entries.append(f"Updated Gradle Wrapper to `{latest_gradle}` (was `{running_gradle}`)")
            gradle_updated = True
    else:
        print("Warning: report.json not found. Run ./gradlew dependencyUpdates first.", file=sys.stderr)

    # Apply updates to settings.gradle.kts
    settings_updated = False
    if updates:
        settings_updates = {k: v for k, v in updates.items() if k != "gradle-wrapper"}
        if settings_updates:
            settings_updated = update_settings_file(settings_updates)

        # Update Gradle wrapper if needed
        if "gradle-wrapper" in updates:
            new_gradle = updates["gradle-wrapper"]
            print(f"Running wrapper update command to version {new_gradle}...")
            if os.name == "nt":
                cmd = "gradlew.bat wrapper --gradle-version " + new_gradle + " --no-daemon"
                shell = True
            else:
                cmd = ["./gradlew", "wrapper", "--gradle-version", new_gradle, "--no-daemon"]
                shell = False
            try:
                subprocess.check_call(cmd, shell=shell)
                print("Successfully updated Gradle Wrapper.")
            except Exception as e:
                print(f"Error updating Gradle Wrapper: {e}", file=sys.stderr)

    updated_flag = "true" if (settings_updated or "gradle-wrapper" in updates) else "false"
    next_ver = get_next_version()

    print(f"\nSummary of updates (Updated: {updated_flag}):")
    for entry in changelog_entries:
        print(f"- {entry}")

    github_output = os.environ.get("GITHUB_OUTPUT")
    if github_output:
        with open(github_output, "a") as f:
            f.write(f"updated={updated_flag}\n")
            f.write(f"gradle_updated={'true' if gradle_updated else 'false'}\n")
            f.write(f"next_version={next_ver}\n")
            
        github_summary = os.environ.get("GITHUB_STEP_SUMMARY")
        if github_summary:
            with open(github_summary, "w") as f:
                f.write("### Dependency Scan Results\n")
                if changelog_entries:
                    f.write(f"**Status: Found updates! Proposed next release version: {next_ver}**\n\n")
                    for entry in changelog_entries:
                        f.write(f"- {entry}\n")
                else:
                    f.write("**Status: All dependencies are up to date!**\n")

    # Save details to build for release phase ingestion
    build_dir = os.path.join(ROOT_DIR, "build")
    os.makedirs(build_dir, exist_ok=True)
    with open(os.path.join(build_dir, "dependency_changes.json"), "w") as f:
        json.dump({
            "updated": updated_flag == "true",
            "gradle_updated": gradle_updated,
            "next_version": next_ver,
            "changes": changelog_entries
        }, f, indent=2)

if __name__ == "__main__":
    main()
