import numpy as np
import triton_python_backend_utils as pb_utils

class TritonPythonModel:
    def execute(self, requests):
        responses = []
        for request in requests:
            input_tensor = pb_utils.get_input_tensor_by_name(request, "INPUT")
            input_data = input_tensor.as_numpy()

            # simple identity (or multiply)
            output_data = input_data * 2

            output_tensor = pb_utils.Tensor("OUTPUT", output_data)
            responses.append(pb_utils.InferenceResponse(output_tensors=[output_tensor]))

        return responses
