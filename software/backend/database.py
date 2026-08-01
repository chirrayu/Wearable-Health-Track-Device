#Your database models and connection setup (SQLite for simple start, PostgreSQL for production).
#All other files import from here.

from sqlalchemy import (
    create_engine, Column, String, Integer,
    Float, Boolean, DateTime, Text, ForeignKey
)
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker, relationship
from datetime import datetime
from config import DATABASE_URL

# ── Engine + session ──────────────────────────────────────────────
_connect_args = {"check_same_thread": False} if DATABASE_URL.startswith("sqlite") else {}
engine = create_engine(
    DATABASE_URL,
    connect_args=_connect_args
)

SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()


# ── Models ────────────────────────────────────────────────────────

class Squad(Base):
    __tablename__ = "squads"

    id         = Column(String, primary_key=True)
    name       = Column(String, unique=True, nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow)

    soldiers = relationship("SoldierModel", back_populates="squad_rel")


class SoldierModel(Base):
    __tablename__ = "soldiers"

    id          = Column(String, primary_key=True)
    name        = Column(String, nullable=False)
    rank_title  = Column(String, nullable=False)
    rank_order  = Column(Integer, nullable=False)
    serial      = Column(String, unique=True, nullable=False)
    squad_id    = Column(String, ForeignKey("squads.id"))
    role        = Column(String)
    blood_group = Column(String, default="O+")
    photo_path  = Column(String, nullable=True)
    status      = Column(String, default="stable")
    created_at  = Column(DateTime, default=datetime.utcnow)

    squad_rel   = relationship("Squad", back_populates="soldiers")
    vitals      = relationship("VitalsModel", back_populates="soldier")
    alerts      = relationship("AlertModel", back_populates="soldier")
    location    = relationship("LocationModel", back_populates="soldier")
    suit_config = relationship("SuitConfigModel", back_populates="soldier", uselist=False)


class VitalsModel(Base):
    __tablename__ = "vitals"

    id          = Column(Integer, primary_key=True, autoincrement=True)
    soldier_id  = Column(String, ForeignKey("soldiers.id"), nullable=False)
    hr          = Column(Integer, nullable=True)
    spo2        = Column(Integer, nullable=True)
    temp        = Column(Float, nullable=True)
    battery     = Column(Integer, nullable=True)
    recorded_at = Column(DateTime, default=datetime.utcnow)

    # ⚠ NEW — required by vitals.py's ingest endpoint and triage.py's
    # calculate_score(). Without these, vitals.py raises a TypeError the
    # moment a request with the new fields comes in.
    activity_index   = Column(Integer, nullable=True)   # 0-3, from firmware's accelerometer sampling
    respiratory_rate  = Column(Integer, nullable=True)   # breaths/min — no sensor yet, always None for now
    blast_severity    = Column(Float, nullable=True)     # 0.0-0.5, set by blast.compute_blast_severity()
    blast_timestamp   = Column(DateTime, nullable=True)  # when a blast-magnitude spike was detected
    score             = Column(Float, nullable=True)     # TA-CSS score set by triage.calculate_score()
    classification    = Column(String, nullable=True)    # "Stable" / "Serious" / "Critical"

    soldier = relationship("SoldierModel", back_populates="vitals")


class AlertModel(Base):
    __tablename__ = "alerts"

    id              = Column(String, primary_key=True)
    soldier_id      = Column(String, ForeignKey("soldiers.id"), nullable=False)
    title           = Column(String, nullable=False)
    severity        = Column(String, nullable=False)
    message         = Column(Text)
    action_required = Column(Boolean, default=False)
    created_at      = Column(DateTime, default=datetime.utcnow)

    soldier = relationship("SoldierModel", back_populates="alerts")


class LocationModel(Base):
    __tablename__ = "locations"

    id          = Column(Integer, primary_key=True, autoincrement=True)
    soldier_id  = Column(String, ForeignKey("soldiers.id"), nullable=False)
    latitude    = Column(Float, nullable=False)
    longitude   = Column(Float, nullable=False)
    recorded_at = Column(DateTime, default=datetime.utcnow)

    soldier = relationship("SoldierModel", back_populates="location")


class SuitConfigModel(Base):
    __tablename__ = "suit_configs"

    soldier_id          = Column(String, ForeignKey("soldiers.id"), primary_key=True)
    hr_sensor           = Column(Boolean, default=True)
    spo2_sensor         = Column(Boolean, default=True)
    temp_sensor         = Column(Boolean, default=True)
    accelerometer       = Column(Boolean, default=True)
    gps_enabled         = Column(Boolean, default=True)
    sampling_rate_secs  = Column(Integer, default=5)
    wifi_enabled        = Column(Boolean, default=True)
    mesh_enabled        = Column(Boolean, default=True)
    radio_gateway       = Column(Boolean, default=False)
    emergency_mode      = Column(Boolean, default=False)
    updated_at          = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    soldier = relationship("SoldierModel", back_populates="suit_config")


class AdminCredential(Base):
    __tablename__ = "admin_credentials"

    id            = Column(String, primary_key=True, default="admin")
    password_hash = Column(String, nullable=False)



# ── DB init helper ────────────────────────────────────────────────
def init_db():
    Base.metadata.create_all(bind=engine)

    db = SessionLocal()
    try:
        if db.query(SoldierModel).first():
            return

        squad = db.query(Squad).filter(Squad.name == "Alpha").first()
        if not squad:
            squad = Squad(id="squad-alpha", name="Alpha")
            db.add(squad)
            db.flush()

        demo_soldier = SoldierModel(
            id="soldier-demo-001",
            name="Demo Soldier",
            rank_title="Pvt",
            rank_order=1,
            serial="SOLDIER-001",
            squad_id=squad.id,
            status="stable",
        )
        db.add(demo_soldier)
        db.commit()
    finally:
        db.close()


def get_soldier_by_ref(db, soldier_ref: str):
    """Resolve a soldier from either the internal DB id or the public serial number."""
    if not soldier_ref:
        return None
    return (
        db.query(SoldierModel)
        .filter(
            (SoldierModel.id == soldier_ref) | (SoldierModel.serial == soldier_ref.upper())
        )
        .first()
    )


# ── Dependency for FastAPI routes ─────────────────────────────────
def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()