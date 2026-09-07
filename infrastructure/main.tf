terraform {
  required_version = ">= 1.12.0"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 6.0" }
  }
}

provider "aws" {
  region                      = var.aws_region
  access_key                  = var.aws_endpoint_url == null ? null : "test"
  secret_key                  = var.aws_endpoint_url == null ? null : "test"
  skip_credentials_validation = var.aws_endpoint_url != null
  skip_metadata_api_check     = var.aws_endpoint_url != null
  skip_requesting_account_id  = var.aws_endpoint_url != null
  s3_use_path_style           = var.aws_endpoint_url != null
  endpoints {
    apigatewayv2 = var.aws_endpoint_url
    apigateway = var.aws_endpoint_url
    cloudwatch = var.aws_endpoint_url
    dynamodb   = var.aws_endpoint_url
    events     = var.aws_endpoint_url
    iam        = var.aws_endpoint_url
    lambda     = var.aws_endpoint_url
    logs       = var.aws_endpoint_url
    s3         = var.aws_endpoint_url
  }
}

locals {
  prefix      = "ghostdrop-${var.environment}"
  bucket_name = "${local.prefix}-files"
  table_name  = "${local.prefix}-files"
  lambda_names = {
    upload   = "${local.prefix}-create-upload"
    info     = "${local.prefix}-get-file"
    download = "${local.prefix}-create-download"
    delete   = "${local.prefix}-delete-file"
    confirm  = "${local.prefix}-confirm-upload"
    cleanup  = "${local.prefix}-cleanup"
  }
}

resource "aws_s3_bucket" "files" { bucket = local.bucket_name }

resource "aws_s3_bucket_public_access_block" "files" {
  bucket                  = aws_s3_bucket.files.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "files" {
  bucket = aws_s3_bucket.files.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_cors_configuration" "files" {
  bucket = aws_s3_bucket.files.id
  cors_rule {
    allowed_headers = ["content-type"]
    allowed_methods = ["PUT", "GET"]
    allowed_origins = [var.allowed_origin]
    expose_headers  = ["etag"]
    max_age_seconds = 300
  }
}

resource "aws_dynamodb_table" "files" {
  name         = local.table_name
  billing_mode = "PAY_PER_REQUEST"
  hash_key = "id"
  attribute {
    name = "id"
    type = "S"
  }
  attribute {
    name = "expirationBucket"
    type = "S"
  }
  attribute {
    name = "expiresAt"
    type = "N"
  }
  attribute {
    name = "storageKey"
    type = "S"
  }
  global_secondary_index {
    name            = "expiration-bucket-index"
    hash_key        = "expirationBucket"
    range_key       = "expiresAt"
    projection_type = "ALL"
  }
  global_secondary_index {
    name            = "storage-key-index"
    hash_key        = "storageKey"
    projection_type = "ALL"
  }
  ttl {
    attribute_name = "expiresAt"
    enabled = true
  }
}

resource "aws_iam_role" "lambda" {
  name = "${local.prefix}-lambda"
  assume_role_policy = jsonencode({ Version = "2012-10-17", Statement = [{ Effect = "Allow", Principal = { Service = "lambda.amazonaws.com" }, Action = "sts:AssumeRole" }] })
}

resource "aws_iam_role_policy" "lambda" {
  name = "${local.prefix}-lambda"
  role = aws_iam_role.lambda.id
  policy = jsonencode({ Version = "2012-10-17", Statement = [
    { Effect = "Allow", Action = ["dynamodb:GetItem", "dynamodb:PutItem", "dynamodb:UpdateItem", "dynamodb:DeleteItem", "dynamodb:Query"], Resource = [aws_dynamodb_table.files.arn, "${aws_dynamodb_table.files.arn}/index/*"] },
    { Effect = "Allow", Action = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"], Resource = ["${aws_s3_bucket.files.arn}/uploads/*"] },
    { Effect = "Allow", Action = ["logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents"], Resource = "*" }
  ] })
}

resource "aws_lambda_function" "handlers" {
  for_each      = local.lambda_names
  function_name = each.value
  role          = aws_iam_role.lambda.arn
  runtime       = "provided.al2023"
  handler = {
    upload = "dev.ghostdrop.api.CreateUploadHandler::handleRequest"
    info = "dev.ghostdrop.api.GetFileHandler::handleRequest"
    download = "dev.ghostdrop.api.CreateDownloadHandler::handleRequest"
    delete = "dev.ghostdrop.api.DeleteFileHandler::handleRequest"
    confirm = "dev.ghostdrop.api.UploadConfirmationHandler::handleRequest"
    cleanup = "dev.ghostdrop.api.ExpiredFileCleanupHandler::handleRequest"
  }[each.key]
  filename         = var.lambda_artifact
  source_code_hash = filebase64sha256(var.lambda_artifact)
  timeout          = 30
  memory_size      = 1024
  environment {
    variables = { FILES_TABLE = aws_dynamodb_table.files.name, FILES_BUCKET = aws_s3_bucket.files.bucket, GHOSTDROP_ALLOWED_ORIGIN = var.allowed_origin, GHOSTDROP_ENVIRONMENT = var.environment }
  }
}

resource "aws_apigatewayv2_api" "api" {
  name          = "${local.prefix}-api"
  protocol_type = "HTTP"
  cors_configuration {
    allow_origins = [var.allowed_origin]
    allow_methods = ["GET", "POST", "DELETE"]
    allow_headers = ["content-type", "authorization"]
    max_age       = 300
  }
}

resource "aws_apigatewayv2_integration" "api" {
  for_each               = { upload = aws_lambda_function.handlers["upload"], info = aws_lambda_function.handlers["info"], download = aws_lambda_function.handlers["download"], delete = aws_lambda_function.handlers["delete"] }
  api_id                 = aws_apigatewayv2_api.api.id
  integration_type       = "AWS_PROXY"
  integration_uri        = each.value.invoke_arn
  payload_format_version = "2.0"
}

resource "aws_apigatewayv2_route" "api" {
  for_each  = { "POST /api/v1/uploads" = "upload", "GET /api/v1/files/{id}" = "info", "POST /api/v1/files/{id}/downloads" = "download", "DELETE /api/v1/files/{id}" = "delete" }
  api_id    = aws_apigatewayv2_api.api.id
  route_key = each.key
  target    = "integrations/${aws_apigatewayv2_integration.api[each.value].id}"
}

resource "aws_lambda_permission" "api" {
  for_each      = aws_apigatewayv2_integration.api
  statement_id  = "AllowApiGateway${title(each.key)}"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.handlers[each.key].function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.api.execution_arn}/*/*"
}

resource "aws_apigatewayv2_stage" "api" {
  api_id      = aws_apigatewayv2_api.api.id
  name        = "$default"
  auto_deploy = true
}

resource "aws_s3_bucket_notification" "confirm" {
  bucket = aws_s3_bucket.files.id
  lambda_function {
    lambda_function_arn = aws_lambda_function.handlers["confirm"].arn
    events = ["s3:ObjectCreated:*"]
    filter_prefix = "uploads/"
  }
  depends_on = [aws_lambda_permission.s3]
}

resource "aws_lambda_permission" "s3" {
  statement_id  = "AllowS3UploadConfirmation"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.handlers["confirm"].function_name
  principal     = "s3.amazonaws.com"
  source_arn    = aws_s3_bucket.files.arn
}

resource "aws_cloudwatch_event_rule" "cleanup" {
  name                = "${local.prefix}-cleanup"
  schedule_expression = "rate(5 minutes)"
}

resource "aws_cloudwatch_event_target" "cleanup" {
  rule = aws_cloudwatch_event_rule.cleanup.name
  arn  = aws_lambda_function.handlers["cleanup"].arn
}

resource "aws_lambda_permission" "cleanup" {
  statement_id  = "AllowEventBridgeCleanup"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.handlers["cleanup"].function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.cleanup.arn
}
