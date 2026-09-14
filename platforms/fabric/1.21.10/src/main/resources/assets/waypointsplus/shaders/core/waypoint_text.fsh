#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
    float LineWidth;
};

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 sampleColor = texture(Sampler0, texCoord0);
#ifdef WAYPOINT_INTENSITY
    sampleColor = sampleColor.rrrr;
#endif
    vec4 color = sampleColor * vertexColor;
    if (color.a < 0.1) {
        discard;
    }
    fragColor = color * ColorModulator;
}
