import boto3
from botocore.exceptions import ClientError
import uuid
import os
from config import (
    AWS_ACCESS_KEY_ID,
    AWS_SECRET_ACCESS_KEY,
    AWS_REGION,
    S3_BUCKET_NAME
)

# ── S3 client ─────────────────────────────────────────────────────
s3_client = boto3.client(
    "s3",
    aws_access_key_id=AWS_ACCESS_KEY_ID,
    aws_secret_access_key=AWS_SECRET_ACCESS_KEY,
    region_name=AWS_REGION
)


def upload_photo(file_bytes: bytes, content_type: str, soldier_id: str) -> str:
    """
    Uploads a photo to S3.
    Returns the S3 key (not a public URL — we use presigned URLs).
    """
    ext = content_type.split("/")[-1]   # e.g. "image/jpeg" → "jpeg"
    key = f"soldiers/{soldier_id}/photo.{ext}"

    try:
        s3_client.put_object(
            Bucket=S3_BUCKET_NAME,
            Key=key,
            Body=file_bytes,
            ContentType=content_type
        )
        return key
    except ClientError as e:
        raise Exception(f"S3 upload failed: {str(e)}")


def delete_photo(s3_key: str):
    """Deletes a photo from S3 by its key."""
    try:
        s3_client.delete_object(Bucket=S3_BUCKET_NAME, Key=s3_key)
    except ClientError as e:
        raise Exception(f"S3 delete failed: {str(e)}")


def get_presigned_url(s3_key: str, expires_in: int = 3600) -> str:
    """
    Generates a temporary URL to access the photo.
    expires_in = seconds until URL expires (default 1 hour).
    The Android app uses this URL to display the image.
    """
    try:
        url = s3_client.generate_presigned_url(
            "get_object",
            Params={
                "Bucket": S3_BUCKET_NAME,
                "Key": s3_key
            },
            ExpiresIn=expires_in
        )
        return url
    except ClientError as e:
        raise Exception(f"Failed to generate presigned URL: {str(e)}")


def photo_exists(s3_key: str) -> bool:
    """Check if a photo exists in S3."""
    try:
        s3_client.head_object(Bucket=S3_BUCKET_NAME, Key=s3_key)
        return True
    except ClientError:
        return False