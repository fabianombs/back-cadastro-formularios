# =============================================================================
# Versoes e backend de estado (FABIANO-10)
# =============================================================================
terraform {
  # 1.5 e o minimo por causa dos blocos `import` em imports.tf. Antes disso o
  # import so existia como comando de CLI, que nao aparece no plan e nao passa
  # por revisao — exatamente o que este card quer evitar.
  # 1.10 e o minimo por causa do use_lockfile no backend acima. Os blocos
  # `import` de imports.tf pediam 1.5; o backend elevou o piso.
  required_version = ">= 1.10.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.60"
    }
  }

  # ---------------------------------------------------------------------------
  # BACKEND REMOTO — ATIVO desde 06/08/2026
  # ---------------------------------------------------------------------------
  # Bucket criado pelo bootstrap-state.sh: versionado, cifrado, com acesso
  # publico bloqueado e TLS obrigatorio.
  #
  # O STATE GUARDA A SENHA DO BANCO EM TEXTO CLARO. E por isso que o bucket e
  # privado e que .tfstate esta no .gitignore.
  backend "s3" {
    bucket       = "fabiano-tfstate-135133927228"
    key          = "producao/terraform.tfstate"
    region       = "us-east-1"
    encrypt      = true
    # Bloqueio nativo via arquivo no proprio S3, exige Terraform >= 1.10.
    # Dispensa a tabela DynamoDB que a documentacao antiga pedia.
    use_lockfile = true
  }
}
