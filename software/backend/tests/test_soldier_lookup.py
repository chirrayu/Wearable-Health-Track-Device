import sys
from pathlib import Path

import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

sys.path.append(str(Path(__file__).resolve().parents[1]))

from database import Base, Squad, SoldierModel, get_soldier_by_ref


@pytest.fixture()
def db_session():
    engine = create_engine("sqlite:///:memory:")
    Base.metadata.create_all(bind=engine)
    Session = sessionmaker(bind=engine)
    session = Session()
    try:
        yield session
    finally:
        session.close()


def test_resolve_soldier_by_serial_and_id(db_session):
    squad = Squad(id="squad-1", name="Alpha")
    db_session.add(squad)
    db_session.flush()

    soldier = SoldierModel(
        id="soldier-uuid-1",
        name="John Doe",
        rank_title="Pvt",
        rank_order=1,
        serial="SOLDIER-001",
        squad_id=squad.id,
    )
    db_session.add(soldier)
    db_session.commit()

    assert get_soldier_by_ref(db_session, "SOLDIER-001").id == soldier.id
    assert get_soldier_by_ref(db_session, soldier.id).id == soldier.id
