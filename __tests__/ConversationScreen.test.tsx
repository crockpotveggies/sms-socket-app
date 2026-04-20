/**
 * @format
 */

import React from 'react';
import {Image, TextInput} from 'react-native';
import ReactTestRenderer from 'react-test-renderer';
import {ConversationScreen} from '../src/screens/messages/ConversationScreen';
import {GatewayAttachment, SmsMessage} from '../src/SmsGateway';

const imageAttachment: GatewayAttachment = {
  id: 'att-1',
  fileName: 'photo.jpg',
  mimeType: 'image/jpeg',
  sizeBytes: 4096,
  base64: 'ZmFrZS1pbWFnZQ==',
  previewBase64: 'cHJldmlldy1pbWFnZQ==',
};

const pdfAttachment: GatewayAttachment = {
  id: 'att-2',
  fileName: 'report.pdf',
  mimeType: 'application/pdf',
  sizeBytes: 2048,
  base64: 'ZmFrZS1wZGY=',
};

const baseMessage: SmsMessage = {
  id: 'msg-1',
  kind: 'mms',
  threadId: 'thread-1',
  address: '+15551234567',
  participants: ['+15551234567'],
  displayName: 'Taylor',
  initials: 'TY',
  body: 'hello with media',
  timestamp: 1_713_139_200_000,
  messageType: 1,
  read: true,
  status: null,
  subject: null,
  hasMedia: true,
  attachments: [imageAttachment],
};

describe('ConversationScreen', () => {
  it('renders composer attachment preview and fires attachment actions', async () => {
    const onPickAttachment = jest.fn();
    const onClearAttachment = jest.fn();
    const onSend = jest.fn();

    let renderer: ReactTestRenderer.ReactTestRenderer;
    await ReactTestRenderer.act(async () => {
      renderer = ReactTestRenderer.create(
        <ConversationScreen
          title="Taylor"
          subtitle="+15551234567"
          address="+15551234567"
          body="caption"
          attachment={imageAttachment}
          loading={false}
          refreshing={false}
          sending={false}
          messages={[baseMessage]}
          onBack={jest.fn()}
          onChangeAddress={jest.fn()}
          onChangeBody={jest.fn()}
          onPickAttachment={onPickAttachment}
          onClearAttachment={onClearAttachment}
          onSend={onSend}
          editableAddress={false}
        />,
      );
    });

    const root = renderer!.root;
    const pressables = root.findAll(node => typeof node.props.onPress === 'function');

    expect(root.findAllByType(Image).length).toBeGreaterThan(0);
    expect(root.findByProps({placeholder: 'Add a caption'})).toBeTruthy();
    expect(pressables.length).toBeGreaterThanOrEqual(4);

    await ReactTestRenderer.act(async () => {
      pressables[1].props.onPress();
      pressables[2].props.onPress();
      pressables[3].props.onPress();
    });

    expect(onClearAttachment).toHaveBeenCalledTimes(1);
    expect(onPickAttachment).toHaveBeenCalledTimes(1);
    expect(onSend).toHaveBeenCalledTimes(1);
  });

  it('renders non-image attachments without an inline image preview', async () => {
    let renderer: ReactTestRenderer.ReactTestRenderer;
    await ReactTestRenderer.act(async () => {
      renderer = ReactTestRenderer.create(
        <ConversationScreen
          title="Taylor"
          subtitle="+15551234567"
          address="+15551234567"
          body=""
          attachment={null}
          loading={false}
          refreshing={false}
          sending={false}
          messages={[{...baseMessage, attachments: [pdfAttachment]}]}
          onBack={jest.fn()}
          onChangeAddress={jest.fn()}
          onChangeBody={jest.fn()}
          onPickAttachment={jest.fn()}
          onClearAttachment={jest.fn()}
          onSend={jest.fn()}
          editableAddress={false}
        />,
      );
    });

    const root = renderer!.root;
    const tree = JSON.stringify(renderer!.toJSON());

    expect(root.findAllByType(Image)).toHaveLength(0);
    expect(tree).toContain('report.pdf');
    expect(tree).toContain('application/pdf');
  });

  it('shows the empty-state copy and editable address field for new threads', async () => {
    let renderer: ReactTestRenderer.ReactTestRenderer;
    const onChangeAddress = jest.fn();

    await ReactTestRenderer.act(async () => {
      renderer = ReactTestRenderer.create(
        <ConversationScreen
          title="New message"
          subtitle="Draft"
          address=""
          body=""
          attachment={null}
          loading={false}
          refreshing={false}
          sending={false}
          messages={[]}
          onBack={jest.fn()}
          onChangeAddress={onChangeAddress}
          onChangeBody={jest.fn()}
          onPickAttachment={jest.fn()}
          onClearAttachment={jest.fn()}
          onSend={jest.fn()}
          editableAddress
        />,
      );
    });

    const root = renderer!.root;
    const tree = JSON.stringify(renderer!.toJSON());

    expect(tree).toContain('Send the first message below to start the thread.');

    const input = root.findAllByType(TextInput)[0];
    await ReactTestRenderer.act(async () => {
      input.props.onChangeText('+15557654321');
    });

    expect(onChangeAddress).toHaveBeenCalledWith('+15557654321');
  });

  it('renders outgoing failure and rejection status details', async () => {
    let renderer: ReactTestRenderer.ReactTestRenderer;

    await ReactTestRenderer.act(async () => {
      renderer = ReactTestRenderer.create(
        <ConversationScreen
          title="Taylor"
          subtitle="+15551234567"
          address="+15551234567"
          body=""
          attachment={null}
          loading={false}
          refreshing={false}
          sending={false}
          messages={[
            {
              ...baseMessage,
              id: 'msg-failed',
              messageType: 2,
              deliveryState: 'failed',
              carrierAccepted: false,
              failureReason: 'Mobile data is disabled.',
            },
            {
              ...baseMessage,
              id: 'msg-rejected',
              messageType: 2,
              deliveryState: 'rejected',
              carrierAccepted: false,
              failureReason: 'Carrier rejected the attachment or content in this MMS.',
            },
          ]}
          onBack={jest.fn()}
          onChangeAddress={jest.fn()}
          onChangeBody={jest.fn()}
          onPickAttachment={jest.fn()}
          onClearAttachment={jest.fn()}
          onSend={jest.fn()}
          editableAddress={false}
        />,
      );
    });

    const tree = JSON.stringify(renderer!.toJSON());

    expect(tree).toContain('Failed before carrier handoff');
    expect(tree).toContain('Mobile data is disabled.');
    expect(tree).toContain('Carrier rejected this MMS');
    expect(tree).toContain('Carrier rejected the attachment or content in this MMS.');
  });

  it('shows a PDF carrier-compatibility warning in the composer preview', async () => {
    let renderer: ReactTestRenderer.ReactTestRenderer;

    await ReactTestRenderer.act(async () => {
      renderer = ReactTestRenderer.create(
        <ConversationScreen
          title="Taylor"
          subtitle="+15551234567"
          address="+15551234567"
          body=""
          attachment={pdfAttachment}
          loading={false}
          refreshing={false}
          sending={false}
          messages={[]}
          onBack={jest.fn()}
          onChangeAddress={jest.fn()}
          onChangeBody={jest.fn()}
          onPickAttachment={jest.fn()}
          onClearAttachment={jest.fn()}
          onSend={jest.fn()}
          editableAddress={false}
        />,
      );
    });

    const tree = JSON.stringify(renderer!.toJSON());
    expect(tree).toContain(
      'PDF MMS support varies by carrier and may fail even when images work.',
    );
  });
});
