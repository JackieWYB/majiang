#!/bin/bash

# Database setup script for production deployment
set -e

# Configuration
DB_HOST=${DB_HOST:-localhost}
DB_PORT=${DB_PORT:-3306}
DB_NAME=${DB_NAME:-mahjong_game}
DB_USER=${DB_USER:-mahjong}
DB_PASSWORD=${DB_PASSWORD:-mahjongpass}
DB_ROOT_PASSWORD=${DB_ROOT_PASSWORD:-rootpassword}

echo "Setting up database for Mahjong Game..."

# Wait for MySQL to be ready
echo "Waiting for MySQL to be ready..."
while ! mysqladmin ping -h"$DB_HOST" -P"$DB_PORT" --silent; do
    echo "Waiting for MySQL..."
    sleep 2
done

echo "MySQL is ready!"

# Create database if it doesn't exist
mysql -h"$DB_HOST" -P"$DB_PORT" -uroot -p"$DB_ROOT_PASSWORD" -e "CREATE DATABASE IF NOT EXISTS $DB_NAME CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# Create user if it doesn't exist
mysql -h"$DB_HOST" -P"$DB_PORT" -uroot -p"$DB_ROOT_PASSWORD" -e "CREATE USER IF NOT EXISTS '$DB_USER'@'%' IDENTIFIED BY '$DB_PASSWORD';"

# Grant privileges
mysql -h"$DB_HOST" -P"$DB_PORT" -uroot -p"$DB_ROOT_PASSWORD" -e "GRANT ALL PRIVILEGES ON $DB_NAME.* TO '$DB_USER'@'%';"
mysql -h"$DB_HOST" -P"$DB_PORT" -uroot -p"$DB_ROOT_PASSWORD" -e "FLUSH PRIVILEGES;"

echo "Database setup completed!"

# Insert default room rules if they don't exist
echo "Inserting default room rules..."
mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" << 'EOF'
INSERT IGNORE INTO t_room_rule (id, name, config) VALUES 
(1, 'Standard 3-Player', '{"players":3,"tiles":"WAN_ONLY","allowPeng":true,"allowGang":true,"allowChi":false,"huTypes":{"basicWin":true,"sevenPairs":true,"allPungs":false,"allHonors":false,"edgeWait":true,"pairWait":true},"score":{"baseScore":2,"dealerMultiplier":2,"selfDrawBonus":1,"maxScore":64,"gangBonus":1},"turn":{"timeLimit":15,"autoTrustee":true,"trusteeDelay":30},"dealer":{"rotation":"WINNER","initialDealer":"RANDOM"},"replay":true,"dismiss":{"ownerOnly":false,"voteRequired":true,"timeoutMinutes":30}}'),
(2, 'Fast Game', '{"players":3,"tiles":"WAN_ONLY","allowPeng":true,"allowGang":true,"allowChi":false,"huTypes":{"basicWin":true,"sevenPairs":true,"allPungs":false,"allHonors":false,"edgeWait":true,"pairWait":true},"score":{"baseScore":2,"dealerMultiplier":2,"selfDrawBonus":1,"maxScore":32,"gangBonus":1},"turn":{"timeLimit":10,"autoTrustee":true,"trusteeDelay":20},"dealer":{"rotation":"WINNER","initialDealer":"RANDOM"},"replay":true,"dismiss":{"ownerOnly":false,"voteRequired":true,"timeoutMinutes":15}}'),
(3, 'Tournament', '{"players":3,"tiles":"WAN_ONLY","allowPeng":true,"allowGang":true,"allowChi":false,"huTypes":{"basicWin":true,"sevenPairs":true,"allPungs":true,"allHonors":false,"edgeWait":true,"pairWait":true},"score":{"baseScore":4,"dealerMultiplier":2,"selfDrawBonus":2,"maxScore":128,"gangBonus":2},"turn":{"timeLimit":20,"autoTrustee":true,"trusteeDelay":45},"dealer":{"rotation":"WINNER","initialDealer":"RANDOM"},"replay":true,"dismiss":{"ownerOnly":true,"voteRequired":false,"timeoutMinutes":60}}');
EOF

echo "Default room rules inserted!"
echo "Database initialization completed successfully!"