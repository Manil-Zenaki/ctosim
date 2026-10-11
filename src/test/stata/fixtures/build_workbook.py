"""Reproduce the small XLSForm fixture with the repository's existing openpyxl."""
from pathlib import Path
from openpyxl import Workbook

workbook = Workbook()
survey = workbook.active
survey.title = "survey"
survey.append(["type", "name", "label", "required"])
survey.append(["text", "answer", "Answer", "yes"])
survey.append(["integer", "quantity", "Quantity", "yes"])
settings = workbook.create_sheet("settings")
settings.append(["form_id", "form_title", "version"])
settings.append(["stata_fixture", "Stata survey", "007"])
workbook.save(Path(__file__).with_name("form.xlsx"))
