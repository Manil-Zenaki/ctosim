"""Namespace-independent assertions on the public XML output."""

import re
import xml.etree.ElementTree as ET


def local_name(element):
    return element.tag.rsplit("}", 1)[-1]


def elements(xml, name):
    root = ET.fromstring(xml) if isinstance(xml, str) else xml
    return [element for element in root.iter() if local_name(element) == name]


def primary_root(result):
    instances = elements(result.xform, "instance")
    assert instances, "XForm must contain a primary instance"
    return list(instances[0])[0]


def bind_for(result, name):
    matches = [bind for bind in elements(result.xform, "bind")
               if bind.get("nodeset", bind.get("ref", "")) == f"/data/{name}"]
    assert len(matches) == 1, f"Expected one bind for {name}"
    return matches[0]


def assert_row_mentioned(message, row):
    assert re.search(rf"(?<!\d){row}(?!\d)", message), message


def instance_paths(result):
    paths = []
    def walk(node, prefix):
        path = prefix + "/" + local_name(node)
        paths.append(path)
        for child in node:
            walk(child, path)
    walk(primary_root(result), "")
    return paths
