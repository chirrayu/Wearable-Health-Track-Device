import sys
from pathlib import Path

sys.path.append(str(Path(__file__).resolve().parents[1]))

from config import normalize_database_url


def test_normalize_database_url_for_render_postgres():
    raw = "postgres://user:pass@host:5432/db"
    assert normalize_database_url(raw) == "postgresql+psycopg2://user:pass@host:5432/db"


def test_normalize_database_url_keeps_sqlite():
    assert normalize_database_url("sqlite:///./triage_ai.db") == "sqlite:///./triage_ai.db"
