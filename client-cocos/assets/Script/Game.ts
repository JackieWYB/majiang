import { _decorator, Component, Node, Label, Prefab } from 'cc';
const { ccclass, property } = _decorator;

// A simple data structure for a player's UI elements
@ccclass('PlayerUI')
export class PlayerUI {
    @property(Node)
    info: Node = null;
    @property(Node)
    hand: Node = null;
    @property(Node)
    melds: Node = null;
    @property(Node)
    discards: Node = null;
}

@ccclass('Game')
export class Game extends Component {

    @property([PlayerUI])
    players: PlayerUI[] = [];

    @property(Node)
    actionPanel: Node = null;

    @property(Label)
    remainingTilesLabel: Label = null;

    @property(Label)
    roundInfoLabel: Label = null;

    @property(Prefab)
    tilePrefab: Prefab = null; // Prefab for a single mahjong tile

    start() {
        console.log('Game scene started');
        this.initializeGame();
    }

    initializeGame() {
        // Placeholder for initializing the game board
        // In a real game, this would be driven by server data
        this.actionPanel.active = false;
        this.updateRemainingTiles(36); // Example starting number
        this.updateRoundInfo("第 1 圈 / 第 1 局");
        this.renderPlayerHand(0, ['1W', '2W', '3W', '5W', '5W']); // Example hand
    }

    // --- UI Update Functions ---

    updateRemainingTiles(count: number) {
        if (this.remainingTilesLabel) {
            this.remainingTilesLabel.string = `剩余牌: ${count}`;
        }
    }

    updateRoundInfo(info: string) {
        if (this.roundInfoLabel) {
            this.roundInfoLabel.string = info;
        }
    }

    renderPlayerHand(playerIndex: number, tiles: string[]) {
        const handNode = this.players[playerIndex]?.hand;
        if (!handNode) return;

        handNode.removeAllChildren();
        for (const tileId of tiles) {
            const tileNode = this.createTileNode(tileId);
            handNode.addChild(tileNode);
        }
    }

    showActionButtons(actions: string[]) {
        this.actionPanel.active = true;
        // Logic to show/hide specific buttons (Pong, Gang, etc.) based on the 'actions' array
        console.log('Available actions:', actions);
    }

    // --- Action Handlers ---

    onPongClick() {
        console.log('Player chose to Pong');
        this.sendActionToServer('PONG');
        this.actionPanel.active = false;
    }

    onGangClick() {
        console.log('Player chose to Gang');
        this.sendActionToServer('GANG');
        this.actionPanel.active = false;
    }

    onHuClick() {
        console.log('Player chose to Hu');
        this.sendActionToServer('HU');
        this.actionPanel.active = false;
    }

    onPassClick() {
        console.log('Player chose to Pass');
        this.sendActionToServer('PASS');
        this.actionPanel.active = false;
    }

    // --- Helper Functions ---

    sendActionToServer(action: string, data: any = {}) {
        console.log(`Sending action: ${action}`, data);
        // WebSocket logic to send the action to the server
    }

    createTileNode(tileId: string): Node {
        // This is a placeholder. A real implementation would instantiate
        // a prefab and set its sprite frame based on the tileId.
        const node = new Node();
        const label = node.addComponent(Label);
        label.string = tileId;
        label.fontSize = 20;
        return node;
    }
}
