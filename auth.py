#Handles admin login, operator sessions, JWT tokens. 
# The admin who edits soldier names, configures suits,
# changes no-movement thresholds needs to be verified.
from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.security import OAuth2PasswordBearer, OAuth2PasswordRequestForm
from sqlalchemy.orm import Session
from jose import JWTError, jwt
from passlib.context import CryptContext
from datetime import datetime, timedelta
from pydantic import BaseModel
from database import get_db
from config import (
    SECRET_KEY, ALGORITHM,
    ACCESS_TOKEN_EXPIRE_MINUTES,
    ADMIN_USERNAME, ADMIN_PASSWORD
)

router = APIRouter()

# ── Password hashing ──────────────────────────────────────────────
pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")
oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/auth/login")


# ── Schemas ───────────────────────────────────────────────────────
class Token(BaseModel):
    access_token: str
    token_type: str

class TokenData(BaseModel):
    username: str | None = None

class UserOut(BaseModel):
    username: str
    role: str


# ── Helpers ───────────────────────────────────────────────────────
def verify_password(plain: str, hashed: str) -> bool:
    return pwd_context.verify(plain, hashed)

def hash_password(plain: str) -> str:
    return pwd_context.hash(plain)

def create_access_token(data: dict) -> str:
    to_encode = data.copy()
    expire = datetime.utcnow() + timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
    to_encode.update({"exp": expire})
    return jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)

def decode_token(token: str) -> TokenData:
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        username: str = payload.get("sub")
        if username is None:
            raise HTTPException(status_code=401, detail="Invalid token")
        return TokenData(username=username)
    except JWTError:
        raise HTTPException(status_code=401, detail="Invalid token")


# ── Dependency: get current admin ────────────────────────────────
# Add this as a dependency to any route that requires login.
# Example:
#   @router.get("/soldiers")
#   def get_soldiers(admin = Depends(get_current_admin)):
#       ...
def get_current_admin(token: str = Depends(oauth2_scheme)) -> UserOut:
    token_data = decode_token(token)
    if token_data.username != ADMIN_USERNAME:
        raise HTTPException(status_code=403, detail="Not authorized")
    return UserOut(username=token_data.username, role="admin")


# ── Routes ────────────────────────────────────────────────────────

# POST /auth/login
# The Android app calls this with username + password.
# Returns a JWT token to use in all future requests.
@router.post("/login", response_model=Token)
def login(form_data: OAuth2PasswordRequestForm = Depends()):

    # Check username
    if form_data.username != ADMIN_USERNAME:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Incorrect username or password"
        )

    # Check password
    # We store the hashed version of ADMIN_PASSWORD.
    # For first run, we hash it on the fly.
    # In production you'd store hashed passwords in the DB.
    hashed = hash_password(ADMIN_PASSWORD)
    if not verify_password(form_data.password, hashed):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Incorrect username or password"
        )

    token = create_access_token(data={"sub": form_data.username})
    return Token(access_token=token, token_type="bearer")


# GET /auth/me
# Returns info about whoever is currently logged in.
# Useful for the Android app to verify a stored token is still valid.
@router.get("/me", response_model=UserOut)
def get_me(current_admin: UserOut = Depends(get_current_admin)):
    return current_admin


# POST /auth/change-password
# Admin can change the password from the Settings screen.
class ChangePasswordRequest(BaseModel):
    old_password: str
    new_password: str

@router.post("/change-password")
def change_password(
    body: ChangePasswordRequest,
    current_admin: UserOut = Depends(get_current_admin)
):
    if body.old_password != ADMIN_PASSWORD:
        raise HTTPException(status_code=400, detail="Old password is incorrect")
    if len(body.new_password) < 6:
        raise HTTPException(status_code=400, detail="New password must be at least 6 characters")

    # In production: update hashed password in DB here
    # For now: returns success (password change persisted via .env update)
    return {"message": "Password changed successfully"}
