import { _decorator, Component, Node, ToggleContainer, Toggle, EditBox, Button } from 'cc';
const { ccclass, property } = _decorator;

@ccclass('CreateRoom')
export class CreateRoom extends Component {

    @property(ToggleContainer)
    roundsToggleContainer: ToggleContainer = null;

    @property(Toggle)
    sevenPairsToggle: Toggle = null;

    @property(Toggle)
    selfDrawOnlyToggle: Toggle = null;

    @property(EditBox)
    maxFanEditBox: EditBox = null;

    start() {
        console.log('CreateRoom UI is ready');
    }

    getRoomConfiguration() {
        const config = {
            max_rounds: 8,
            huTypes: {
                qiDui: false
            },
            score: {
                fanCap: 6
            },
            settleMode: 'DIANPAO_PAYER'
        };

        // Get rounds
        const activeToggle = this.roundsToggleContainer.toggleItems.find(toggle => toggle.isChecked);
        if (activeToggle) {
            config.max_rounds = activeToggle.node.name === '8_rounds' ? 8 : 16;
        }

        // Get gameplay options
        config.huTypes.qiDui = this.sevenPairsToggle.isChecked;
        if (this.selfDrawOnlyToggle.isChecked) {
            // This implies a different settlement mode
            // For now, just log it. A real implementation would map this to a specific config value.
            console.log('Settle mode: Self-draw only');
        }

        // Get score options
        const maxFan = parseInt(this.maxFanEditBox.string, 10);
        if (!isNaN(maxFan) && maxFan > 0) {
            config.score.fanCap = maxFan;
        }

        return config;
    }

    onCreateButtonClick() {
        const config = this.getRoomConfiguration();
        console.log('Creating room with configuration:', JSON.stringify(config, null, 2));
        // Logic to send config to server and create room
    }

    onCloseButtonClick() {
        console.log('Closing Create Room UI');
        this.node.active = false; // Hide the panel
    }
}
