-- Insert default room rules

-- Standard 3-player Mahjong rules
INSERT INTO t_room_rule (name, description, config, is_default, is_active) VALUES 
(
    'Standard 3-Player',
    'Standard 3-player Mahjong with basic rules',
    '{"players":3,"tiles":"WAN_ONLY","allowPeng":true,"allowGang":true,"allowChi":false,"huTypes":{"sevenPairs":true,"allPungs":true,"allHonors":true,"edgeWait":true,"pairWait":true,"selfDraw":true},"score":{"baseScore":2,"maxScore":24,"dealerMultiplier":2.0,"selfDrawBonus":1.0,"gangBonus":1,"multipleWinners":false},"turn":{"turnTimeLimit":15,"actionTimeLimit":2,"autoTrustee":true,"trusteeTimeout":30},"dealer":{"rotateOnWin":false,"rotateOnDraw":true,"rotateOnLose":true},"replay":true,"dismiss":{"requireAllAgree":true,"voteTimeLimit":60,"autoDissolveTimeout":1800}}',
    true,
    true
),
(
    'Fast Game',
    'Quick 3-player game with shorter time limits',
    '{"players":3,"tiles":"WAN_ONLY","allowPeng":true,"allowGang":true,"allowChi":false,"huTypes":{"sevenPairs":true,"allPungs":true,"allHonors":true,"edgeWait":true,"pairWait":true,"selfDraw":true},"score":{"baseScore":2,"maxScore":16,"dealerMultiplier":1.5,"selfDrawBonus":1.0,"gangBonus":1,"multipleWinners":false},"turn":{"turnTimeLimit":10,"actionTimeLimit":1,"autoTrustee":true,"trusteeTimeout":20},"dealer":{"rotateOnWin":true,"rotateOnDraw":true,"rotateOnLose":true},"replay":true,"dismiss":{"requireAllAgree":false,"voteTimeLimit":30,"autoDissolveTimeout":900}}',
    false,
    true
),
(
    'High Stakes',
    'High scoring game with extended features',
    '{"players":3,"tiles":"WAN_ONLY","allowPeng":true,"allowGang":true,"allowChi":false,"huTypes":{"sevenPairs":true,"allPungs":true,"allHonors":true,"edgeWait":true,"pairWait":true,"selfDraw":true},"score":{"baseScore":4,"maxScore":48,"dealerMultiplier":3.0,"selfDrawBonus":2.0,"gangBonus":2,"multipleWinners":true},"turn":{"turnTimeLimit":20,"actionTimeLimit":3,"autoTrustee":true,"trusteeTimeout":45},"dealer":{"rotateOnWin":false,"rotateOnDraw":true,"rotateOnLose":true},"replay":true,"dismiss":{"requireAllAgree":true,"voteTimeLimit":90,"autoDissolveTimeout":2700}}',
    false,
    true
);