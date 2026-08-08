# =============================================================================
# Adocao dos recursos existentes (FABIANO-10)
# =============================================================================
# Blocos `import` do Terraform >= 1.5, e nao os comandos `terraform import` da
# CLI. A diferenca importa:
#
#   - a CLI importa e pronto: nada aparece no plan, nada passa por revisao
#   - o bloco aparece no `terraform plan` como "will be imported", entra no diff
#     publicado no PR, e some sozinho depois de aplicado
#
# Num repositorio onde o objetivo declarado e "plan vira detector de drift",
# importar por fora do plan seria contraditorio.
#
# DEPOIS DO PRIMEIRO APPLY BEM-SUCEDIDO, ESTE ARQUIVO PODE SER APAGADO.
# Ele e um passo de migracao, nao configuracao permanente. Deixar para tras nao
# quebra nada (import de recurso ja no state e no-op), mas polui.
#
# Os ids vieram do inventario coletado em 04/08/2026 e registrado no card.
# =============================================================================

import {
  to = aws_key_pair.deployer
  id = "poc-fabiano-key"
}

import {
  to = aws_instance.app
  id = "i-0987e63c336e202b9"
}

import {
  to = aws_eip.app
  id = "eipalloc-025082e8787508bb8"
}

import {
  to = aws_db_instance.mysql
  id = "poc-fabiano-db"
}

import {
  to = aws_security_group.ec2
  id = "sg-0a32a93ab12d715f0"
}

import {
  to = aws_security_group.rds
  id = "sg-03e03dbdafd884ba8"
}

# -----------------------------------------------------------------------------
# NAO importados, e o motivo de cada um
# -----------------------------------------------------------------------------
# sg-0e19a5a773c15c01e  — security group "default" da VPC. Nao foi criado por
#                         este projeto e a AWS nao deixa apagar. Gerenciar um
#                         recurso que nao se pode destruir so adiciona ruido.
#
# i-008f8d272588845ef   — a EC2 nova do blue-green. Ela sera substituida pelo
#                         Auto Scaling (modulo ./asg). Importar uma instancia
#                         solta agora, para remover do codigo depois, produziria
#                         um `destroy` no plan da maquina que hoje atende
#                         homologacao.
#
# eipalloc-053acd67132fed0af — Elastic IP da maquina nova (54.197.175.159),
#                         alocado em 06/08. Entra junto com o modulo ./asg,
#                         porque e ele quem passa a associar o endereco no boot.
#
# poc-fabiano-db-ensaio — RDS de ensaio. Temporario por definicao: o FABIANO-48
#                         preve apaga-lo depois da virada. Colocar no state
#                         significaria ter de remove-lo do state depois.
#
# poc-fabiano-mysql84   — parameter group criado pela CLI, ainda nao aplicado a
#                         nenhuma instancia (FABIANO-6). Entra quando for usado.
