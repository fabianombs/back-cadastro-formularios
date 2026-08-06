# =============================================================================
# Versoes e backend de estado (FABIANO-10)
# =============================================================================
terraform {
  # 1.5 e o minimo por causa dos blocos `import` em imports.tf. Antes disso o
  # import so existia como comando de CLI, que nao aparece no plan e nao passa
  # por revisao — exatamente o que este card quer evitar.
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.60"
    }
  }

  # ---------------------------------------------------------------------------
  # BACKEND REMOTO — DESCOMENTAR SO DEPOIS DE CRIAR O BUCKET
  # ---------------------------------------------------------------------------
  # Ordem obrigatoria:
  #   1. rodar ./bootstrap-state.sh (cria o bucket, versionamento e bloqueio publico)
  #   2. descomentar o bloco abaixo
  #   3. terraform init -migrate-state
  #
  # Descomentar antes do passo 1 faz o `terraform init` falhar dizendo que o
  # bucket nao existe — e a mensagem nao deixa obvio que a ordem e o problema.
  #
  # O STATE GUARDA A SENHA DO BANCO EM TEXTO CLARO. Por isso bucket privado,
  # versionado e cifrado, e por isso .tfstate esta no .gitignore.
  #
  # backend "s3" {
  #   bucket       = "fabiano-tfstate-135133927228"
  #   key          = "producao/terraform.tfstate"
  #   region       = "us-east-1"
  #   encrypt      = true
  #   # Bloqueio nativo via arquivo no proprio S3 (Terraform >= 1.10).
  #   # Dispensa a tabela DynamoDB que a documentacao antiga exigia.
  #   use_lockfile = true
  # }
}
