output "api_url" { value = aws_apigatewayv2_stage.api.invoke_url }
output "files_bucket" { value = aws_s3_bucket.files.bucket }
output "files_table" { value = aws_dynamodb_table.files.name }
