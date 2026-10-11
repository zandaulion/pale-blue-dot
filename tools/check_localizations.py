#!/usr/bin/env python3
"""Validate bundled locales, translation coverage, and runtime format placeholders."""
from collections import Counter
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
LOCALES = {
    "de-DE": "de-rDE", "es-ES": "es-rES", "fr-FR": "fr-rFR", "ar": "ar",
    "hi-IN": "hi-rIN", "ja-JP": "ja-rJP", "ko-KR": "ko-rKR", "pt-BR": "pt-rBR",
    "ro": "ro", "uk": "uk", "zh-CN": "zh-rCN",
}


def strings(path):
    elements = list(ET.parse(path).getroot())
    names = [item.attrib["name"] for item in elements]
    assert len(names) == len(set(names)), f"Duplicate resources: {path}"
    return {item.attrib["name"]: item for item in elements}


def formats(value):
    return Counter(re.findall(r"%\d+\$[sd]|%%", value))


def main():
    base = strings(RES / "values/strings.xml")
    translatable = {key for key, element in base.items() if element.get("translatable") != "false"}
    for tag, directory in LOCALES.items():
        localized = strings(RES / f"values-{directory}/strings.xml")
        assert set(localized) == translatable, f"Coverage differs: {tag}"
        for key, element in localized.items():
            value = element.text or ""
            assert value.strip('"'), f"Empty translation: {tag}/{key}"
            assert formats(value) == formats(base[key].text or ""), f"Format mismatch: {tag}/{key}"
            assert not re.search(r"ZXQ|XXQ|\[PBD\d", value), f"Unresolved translation token: {tag}/{key}"
            assert value.count(r"\n") == (base[key].text or "").count(r"\n"), f"Newline mismatch: {tag}/{key}"
    config = ET.parse(RES / "xml/locales_config.xml").getroot()
    declared = [item.attrib["{http://schemas.android.com/apk/res/android}name"] for item in config]
    assert declared == ["en-US", *LOCALES], "App language declaration differs from bundled resources"
    referenced = set()
    for path in (ROOT / "app/src").rglob("*.kt"):
        referenced.update(re.findall(r"(?<!android\.)R\.string\.(\w+)", path.read_text()))
    for path in [ROOT / "app/src/main/AndroidManifest.xml", *RES.rglob("*.xml")]:
        referenced.update(re.findall(r"@string/(\w+)", path.read_text()))
    assert referenced <= set(base), f"Missing default resources: {referenced - set(base)}"
    print(f"Validated all 12 locales: {len(translatable)} translated strings per locale; coverage, placeholders, newlines, declarations, and references match.")


if __name__ == "__main__":
    main()
