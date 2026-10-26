import { Stage } from 'aws-cdk-lib'
import { AppStack } from './app-stack.js'

export class SecretSantaStage extends Stage {
    constructor(scope, id, props) {
        super(scope, id, props)

        new AppStack(this, 'secretsanta-deployment', props)
    }
}
