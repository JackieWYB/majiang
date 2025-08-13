import { _decorator, Component, Node, Label, Button } from 'cc';
const { ccclass, property } = _decorator;

@ccclass('Lobby')
export class Lobby extends Component {

    @property(Label)
    nicknameLabel: Label = null;

    @property(Label)
    coinsLabel: Label = null;

    @property(Label)
    roomCardsLabel: Label = null;

    start() {
        console.log('Lobby scene started');
        this.loadPlayerData();
    }

    loadPlayerData() {
        // Placeholder: Load player data from a service
        if (this.nicknameLabel) {
            this.nicknameLabel.string = '玩家12345';
        }
        if (this.coinsLabel) {
            this.coinsLabel.string = '8888';
        }
        if (this.roomCardsLabel) {
            this.roomCardsLabel.string = '10';
        }
    }

    onQuickStartClick() {
        console.log('Quick Start clicked');
        // Logic to send a match request
    }

    onCreateRoomClick() {
        console.log('Create Room clicked');
        // Logic to show the create room panel/scene
    }

    onJoinRoomClick() {
        console.log('Join Room clicked');
        // Logic to show a join room dialog
    }

    onHistoryClick() {
        console.log('History clicked');
        // Logic to switch to the game history scene
    }

    onSettingsClick() {
        console.log('Settings clicked');
        // Logic to show a settings dialog
    }
}
