from flask import Flask, request, jsonify, send_from_directory
from flask_cors import CORS
from together import Together
import os
from dotenv import load_dotenv
import traceback
import requests
import base64

# Load environment variables
load_dotenv()
print("API Key:", os.getenv('TOGETHER_API_KEY'))
print("Allowed Origins:", os.getenv('ALLOWED_ORIGINS'))

app = Flask(__name__, static_folder='static', static_url_path='')
# Allow CORS for all origins (adjust for production)
CORS(app, resources={r"/generate-image": {"origins": os.getenv('ALLOWED_ORIGINS', '*')}})

@app.route('/')
def serve_frontend():
    return send_from_directory(app.static_folder, 'Deepseek.html')

@app.route('/generate-image', methods=['POST'])
def generate_image():
    try:
        print("Received payload:", request.get_json())
        if not request.is_json:
            return jsonify({'error': 'Request must be JSON'}), 400

        data = request.get_json()
        prompt = data.get('prompt', '').strip()
        
        if not prompt:
            return jsonify({'error': 'Prompt is required'}), 400
        if len(prompt) > 1000:
            return jsonify({'error': 'Prompt is too long'}), 400

        model = data.get('model', 'black-forest-labs/FLUX.1-dev')
        steps = min(max(int(data.get('steps', 10)), 1), 50)
        n = min(max(int(data.get('n', 1)), 1), 10)

        api_key = os.getenv('TOGETHER_API_KEY')
        if not api_key:
            return jsonify({'error': 'Server configuration error: Missing API key'}), 500

        client = Together(api_key=api_key)

        print(f"Calling Together API with prompt: {prompt}, model: {model}, steps: {steps}, n: {n}")
        response = client.images.generate(
            prompt=prompt,
            model=model,
            steps=steps,
            n=n
        )

        print("API response:", vars(response) if hasattr(response, '__dict__') else response)
        if not response.data or not response.data[0]:
            return jsonify({'error': 'No image generated', 'details': 'Empty response from API'}), 500

        # Check for b64_json or url
        image_data = response.data[0].b64_json
        if image_data:
            return jsonify({'image': image_data})

        # Fallback to URL
        image_url = response.data[0].url
        if not image_url:
            return jsonify({'error': 'No image generated', 'details': 'Neither b64_json nor url provided'}), 500

        # Fetch image from URL and convert to base64
        try:
            image_response = requests.get(image_url, timeout=10)
            image_response.raise_for_status()
            image_content = image_response.content
            image_data = base64.b64encode(image_content).decode('utf-8')
            return jsonify({'image': image_data})
        except requests.RequestException as e:
            return jsonify({'error': 'Failed to fetch image', 'details': str(e)}), 500

    except ValueError as ve:
        app.logger.error(f"ValueError: {str(ve)}")
        return jsonify({'error': 'Invalid parameter format', 'details': str(ve)}), 400
    except Exception as e:
        error_details = f"Error generating image: {str(e)}\n{traceback.format_exc()}"
        app.logger.error(error_details)
        print(error_details)
        return jsonify({'error': 'Internal server error', 'details': str(e)}), 500

if __name__ == '__main__':
    app.run(
        host='0.0.0.0',
        port=int(os.getenv('PORT', 5000)),
        debug=os.getenv('FLASK_ENV', 'production') == 'development'
    )
