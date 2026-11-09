import { Client, IMessage, StompSubscription } from '@stomp/stompjs';

class StompManager {
  private client: Client | null = null;
  private token: string | null = null;
  private isConnected = false;

  public initialize(token: string) {
    if (this.client && this.isConnected && this.token === token) {
      return;
    }
    this.token = token;

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const brokerUrl = `${protocol}//${window.location.host}/ws/stomp`;

    this.client = new Client({
      brokerURL: brokerUrl,
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
    });

    this.client.onConnect = () => {
      this.isConnected = true;
    };

    this.client.onDisconnect = () => {
      this.isConnected = false;
    };

    this.client.activate();
  }

  public subscribe(topic: string, callback: (data: any) => void): () => void {
    if (!this.client) {
      return () => {};
    }

    let sub: StompSubscription | null = null;

    const setup = () => {
      if (this.client && this.client.connected) {
        sub = this.client.subscribe(topic, (msg: IMessage) => {
          try {
            const data = JSON.parse(msg.body);
            callback(data);
          } catch {
            callback(msg.body);
          }
        });
      }
    };

    if (this.client.connected) {
      setup();
    } else {
      const prevOnConnect = this.client.onConnect;
      this.client.onConnect = (frame) => {
        this.isConnected = true;
        if (prevOnConnect) prevOnConnect(frame);
        setup();
      };
    }

    return () => {
      if (sub) {
        sub.unsubscribe();
      }
    };
  }

  public disconnect() {
    if (this.client) {
      this.client.deactivate();
      this.client = null;
      this.isConnected = false;
    }
  }
}

export const stompManager = new StompManager();

