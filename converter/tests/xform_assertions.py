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


def assert_choice_list(result, field_name, control_type, list_name, expected):
    """Check Amendment 1's secondary instance and its select reference."""
    instances = [node for node in elements(result.xform, "instance")
                 if node.get("id") == list_name]
    assert len(instances) == 1, f"Expected one secondary instance for {list_name}"
    roots = list(instances[0])
    assert len(roots) == 1 and local_name(roots[0]) == "root"
    choices = []
    for item in roots[0]:
        assert local_name(item) == "item"
        names = [node for node in item if local_name(node) == "name"]
        labels = [node for node in item if local_name(node) == "label"]
        assert len(names) == len(labels) == 1
        choices.append((names[0].text, "".join(labels[0].itertext())))
    assert choices == expected

    controls = [node for node in elements(result.xform, control_type)
                if node.get("ref") == f"/data/{field_name}"]
    assert len(controls) == 1
    assert not elements(controls[0], "item"), "Select choices must use an itemset"
    itemsets = elements(controls[0], "itemset")
    assert len(itemsets) == 1
    assert itemsets[0].get("nodeset", "").strip() == f"instance('{list_name}')/root/item"


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
