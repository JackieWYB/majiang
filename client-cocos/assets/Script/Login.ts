import { _decorator, Component, Node, Toggle, Button } from 'cc';
const { ccclass, property } = _decorator;

@ccclass('Login')
export class Login extends Component {

    @property(Toggle)
    agreementToggle: Toggle = null;

    @property(Button)
    loginButton: Button = null;

    start() {
        console.log('Login scene started');
        this.updateLoginButtonState();
    }

    onAgreementToggle() {
        this.updateLoginButtonState();
    }

    updateLoginButtonState() {
        if (this.loginButton) {
            this.loginButton.interactable = this.agreementToggle.isChecked;
        }
    }

    onLoginClick() {
        if (!this.agreementToggle.isChecked) {
            console.log('Please agree to the terms first.');
            return;
        }
        console.log('Login button clicked. Proceeding with WeChat login...');
        // Here we will add logic for WeChat login API call
    }

    onUserAgreementClick() {
        console.log('Open User Agreement');
        // Logic to open a webview or another panel with the agreement text
    }

    onPrivacyPolicyClick() {
        console.log('Open Privacy Policy');
        // Logic to open a webview or another panel with the policy text
    }
}
