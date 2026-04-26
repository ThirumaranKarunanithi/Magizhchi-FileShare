#!/bin/bash
# Creates the S3 bucket in LocalStack on startup
awslocal s3 mb s3://magizhchi-share-dev
awslocal s3api put-bucket-cors --bucket magizhchi-share-dev --cors-configuration '{
  "CORSRules": [{
    "AllowedHeaders": ["*"],
    "AllowedMethods": ["GET", "PUT", "POST", "DELETE", "HEAD"],
    "AllowedOrigins": ["http://localhost:5173", "http://localhost:8080"],
    "ExposeHeaders":  ["ETag"],
    "MaxAgeSeconds":  3000
  }]
}'
echo "[LocalStack] Bucket magizhchi-share-dev ready."
