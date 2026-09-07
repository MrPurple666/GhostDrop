variable "environment" {
  type    = string
  default = "dev"
}

variable "aws_region" {
  type    = string
  default = "us-east-1"
}

variable "aws_endpoint_url" {
  type     = string
  default  = null
  nullable = true
}

variable "public_endpoint_url" {
  type     = string
  default  = null
  nullable = true
}

variable "allowed_origin" {
  type    = string
  default = "http://localhost:5173"
}

variable "lambda_artifact" {
  type    = string
  default = "../backend/target/ghostdrop-lambda.zip"
}
