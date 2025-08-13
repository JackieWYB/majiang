import { _decorator, Component, Node, Label, Prefab } from 'cc';
const { ccclass, property } = _decorator;

@ccclass('PlayerResultUI')
export class PlayerResultUI {
    @property(Label)
    nicknameLabel: Label = null;

    @property(Label)
    huDetailsLabel: Label = null; // e.g., "自摸, 对对胡"

    @property(Label)
    scoreDeltaLabel: Label = null; // e.g., "+30"

    @property(Label)
    totalScoreLabel: Label = null; // e.g., "总分: 150"

    @property(Node)
    winnerTag: Node = null;
}

@ccclass('Settlement')
export class Settlement extends Component {

    @property(Label)
    titleLabel: Label = null;

    @property([PlayerResultUI])
    playerResults: PlayerResultUI[] = [];

    start() {
        console.log('Settlement UI is ready');
        // In a real scenario, this would be hidden until the game round ends
        // this.node.active = false;
        this.showResults(this.getExampleSettlementData());
    }

    showResults(data) {
        this.node.active = true;

        if (data.isDraw) {
            this.titleLabel.string = "荒庄";
        } else {
            this.titleLabel.string = "本局结算";
        }

        for (let i = 0; i < this.playerResults.length; i++) {
            const resultUI = this.playerResults[i];
            const playerData = data.results[i];

            if (resultUI && playerData) {
                resultUI.nicknameLabel.string = playerData.nickname;
                resultUI.scoreDeltaLabel.string = playerData.scoreDelta > 0 ? `+${playerData.scoreDelta}` : `${playerData.scoreDelta}`;
                resultUI.totalScoreLabel.string = `总分: ${playerData.totalScore}`;

                if (playerData.isWinner) {
                    resultUI.winnerTag.active = true;
                    resultUI.huDetailsLabel.string = playerData.huDetails.join(', ');
                } else {
                    resultUI.winnerTag.active = false;
                    resultUI.huDetailsLabel.string = '';
                }
            }
        }
    }

    onContinueClick() {
        console.log('Continue button clicked');
        // Logic to signal readiness for the next round or return to the lobby
        this.node.active = false;
    }

    onShareClick() {
        console.log('Share button clicked');
        // Logic to capture the screen and use a platform-specific share API
    }

    getExampleSettlementData() {
        // This is a placeholder that simulates the data structure from the server
        return {
            isDraw: false,
            results: [
                { nickname: 'Player 1', isWinner: true, huDetails: ['自摸', '夹胡'], scoreDelta: 24, totalScore: 124 },
                { nickname: 'Player 2', isWinner: false, huDetails: [], scoreDelta: -12, totalScore: 88 },
                { nickname: 'Player 3', isWinner: false, huDetails: [], scoreDelta: -12, totalScore: 90 }
            ]
        };
    }
}
