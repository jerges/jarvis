resource "aws_dynamodb_table" "conversations" {
  name         = "${local.prefix}-conversations"
  billing_mode = var.dynamodb_billing_mode
  hash_key     = "conversation_id"
  range_key    = "timestamp"

  attribute {
    name = "conversation_id"
    type = "S"
  }

  attribute {
    name = "timestamp"
    type = "N"
  }

  ttl {
    attribute_name = "ttl"
    enabled        = true
  }

  point_in_time_recovery {
    enabled = true
  }
}
