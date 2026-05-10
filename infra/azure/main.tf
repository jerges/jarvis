terraform {
  required_version = ">= 1.6"
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
  }
}

provider "azurerm" {
  features {}
}

# ── Resource Group ────────────────────────────────────────────────────────────

resource "azurerm_resource_group" "jarvis" {
  name     = var.resource_group_name
  location = var.location
  tags     = { Project = "jarvis" }
}

# ── Red ───────────────────────────────────────────────────────────────────────

resource "azurerm_virtual_network" "jarvis" {
  name                = "jarvis-vnet"
  address_space       = ["10.0.0.0/16"]
  location            = azurerm_resource_group.jarvis.location
  resource_group_name = azurerm_resource_group.jarvis.name
  tags                = { Project = "jarvis" }
}

resource "azurerm_subnet" "jarvis" {
  name                 = "jarvis-subnet"
  resource_group_name  = azurerm_resource_group.jarvis.name
  virtual_network_name = azurerm_virtual_network.jarvis.name
  address_prefixes     = ["10.0.1.0/24"]
}

resource "azurerm_public_ip" "jarvis" {
  name                = "jarvis-pip"
  location            = azurerm_resource_group.jarvis.location
  resource_group_name = azurerm_resource_group.jarvis.name
  allocation_method   = "Static"
  sku                 = "Standard"
  tags                = { Project = "jarvis" }
}

# ── Network Security Group ────────────────────────────────────────────────────

resource "azurerm_network_security_group" "jarvis" {
  name                = "jarvis-nsg"
  location            = azurerm_resource_group.jarvis.location
  resource_group_name = azurerm_resource_group.jarvis.name
  tags                = { Project = "jarvis" }

  security_rule {
    name                       = "SSH"
    priority                   = 100
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "22"
    source_address_prefix      = var.ssh_allowed_cidr
    destination_address_prefix = "*"
  }

  security_rule {
    name                       = "HTTP"
    priority                   = 110
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "80"
    source_address_prefix      = "*"
    destination_address_prefix = "*"
  }

  security_rule {
    name                       = "HTTPS"
    priority                   = 120
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "443"
    source_address_prefix      = "*"
    destination_address_prefix = "*"
  }
}

resource "azurerm_network_interface" "jarvis" {
  name                = "jarvis-nic"
  location            = azurerm_resource_group.jarvis.location
  resource_group_name = azurerm_resource_group.jarvis.name
  tags                = { Project = "jarvis" }

  ip_configuration {
    name                          = "internal"
    subnet_id                     = azurerm_subnet.jarvis.id
    private_ip_address_allocation = "Dynamic"
    public_ip_address_id          = azurerm_public_ip.jarvis.id
  }
}

resource "azurerm_network_interface_security_group_association" "jarvis" {
  network_interface_id      = azurerm_network_interface.jarvis.id
  network_security_group_id = azurerm_network_security_group.jarvis.id
}

# ── Linux VM ──────────────────────────────────────────────────────────────────
# Standard_B1ms → 1 vCPU / 2 GB RAM — ~$15/mes (suficiente para testing)
# Para más comodidad: Standard_B2s (4 GB, ~$30/mes)

resource "azurerm_linux_virtual_machine" "jarvis" {
  name                  = "jarvis-vm"
  resource_group_name   = azurerm_resource_group.jarvis.name
  location              = azurerm_resource_group.jarvis.location
  size                  = var.vm_size
  admin_username        = var.admin_username
  network_interface_ids = [azurerm_network_interface.jarvis.id]
  tags                  = { Project = "jarvis" }

  admin_ssh_key {
    username   = var.admin_username
    public_key = file(var.ssh_public_key_path)
  }

  os_disk {
    caching              = "ReadWrite"
    storage_account_type = "Standard_LRS"
    disk_size_gb         = 30
  }

  # Ubuntu 24.04 LTS
  source_image_reference {
    publisher = "Canonical"
    offer     = "ubuntu-24_04-lts"
    sku       = "server"
    version   = "latest"
  }

  # cloud-init bootstraps Docker + repo + .env + docker compose up
  custom_data = base64encode(templatefile("${path.module}/cloud-init.sh", {
    repo_url                      = var.repo_url
    anthropic_api_key             = var.anthropic_api_key
    jarvis_default_provider       = var.jarvis_default_provider
    azure_openai_api_key          = var.azure_openai_api_key
    azure_openai_endpoint         = var.azure_openai_endpoint
    azure_orchestrator_deployment = var.azure_orchestrator_deployment
    azure_agent_deployment        = var.azure_agent_deployment
    admin_username                = var.admin_username
    telegram_bot_token            = var.telegram_bot_token
    telegram_webhook_secret       = var.telegram_webhook_secret
    whatsapp_access_token         = var.whatsapp_access_token
    whatsapp_phone_number_id      = var.whatsapp_phone_number_id
    whatsapp_verify_token         = var.whatsapp_verify_token
  }))
}
