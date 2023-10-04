#!/usr/bin/env python3

import io
import os
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET


def add_source_exclusions(path):
    tree = ET.parse(path)
    root = tree.getroot()

    ns = 'https://schema.gradle.org/dependency-verification'
    ET.register_namespace('', ns)
    configuration = root.find(f'{{{ns}}}configuration')
    trusted_artifacts = ET.SubElement(
        configuration, f'{{{ns}}}trusted-artifacts')

    for regex in [
        r'.*-javadoc[.]jar',
        r'.*-sources[.]jar',
        r'.*-src[.]zip',
    ]:
        ET.SubElement(trusted_artifacts, f'{{{ns}}}trust', attrib={
            'file': regex,
            'regex': 'true',
        })

    # Match gradle's formatting exactly.
    ET.indent(tree, '   ')
    root.tail = '\n'

    with io.BytesIO() as f:
        # etree's xml_declaration=True uses single quotes in the header.
        f.write(b'<?xml version="1.0" encoding="UTF-8"?>\n')
        tree.write(f)
        serialized = f.getvalue().replace(b' />', b'/>')

    with open(path, 'wb') as f:
        f.write(serialized)


def main():
    root_dir = os.path.join(sys.path[0], '..')
    xml_file = os.path.join(sys.path[0], 'verification-metadata.xml')

    try:
        os.remove(xml_file)
    except FileNotFoundError:
        pass

    # Gradle will sometimes fail to add verification entries for artifacts that
    # are already cached.
    with tempfile.TemporaryDirectory() as temp_dir:
        env = os.environ | {'GRADLE_USER_HOME': temp_dir}

        subprocess.check_call(
            [
                './gradlew' + ('.bat' if os.name == 'nt' else ''),
                '--write-verification-metadata', 'sha512',
                '--no-daemon',
                'build',
                'zipDebug',
                # Requires signing.
                '-x', 'assembleRelease',
            ],
            env=env,
            cwd=root_dir,
        )

    # Add exclusions to allow Android Studio to download sources.
    add_source_exclusions(xml_file)


if __name__ == '__main__':
    main()
